package com.latifi.wallet.data.wallet

import com.latifi.wallet.core.Hex
import com.latifi.wallet.domain.wallet.RequestHandle
import com.latifi.wallet.domain.wallet.WalletFailure
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletResponse
import com.latifi.wallet.domain.wallet.WalletState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URISyntaxException
import kotlin.coroutines.resume

/** Starts a deep link, such as the wallet app of the active session. Android code lives behind this. */
fun interface UriLauncher {
    fun open(uri: String)
}

/**
 * The [WalletGateway] over Reown AppKit. It holds no key and signs nothing: it turns SDK events
 * into [WalletState], sends requests to the wallet and publishes every answer on a process-wide flow.
 *
 * - The state is restored from the stored account at construction, so a process kill keeps the
 *   connection (Reown stores the session).
 * - [sendRequest] returns the request id as soon as the wallet has been asked. The answer comes
 *   later on [responses] with the same id, even after a restart.
 * - A result of 132 characters is a signature, one of 66 a transaction hash, anything else a failure.
 * - [openWalletApp] only follows the wallet deep link. It never calls the SDK `request` again.
 */
class AppKitWalletGateway(
    private val client: AppKitClient,
    private val bridge: AppKitPickerBridge,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val launcher: UriLauncher,
    private val pickerCloseGraceMillis: Long = PICKER_CLOSE_GRACE_MILLIS,
) : WalletGateway {
    private val mutableState = MutableStateFlow<WalletState>(WalletState.Disconnected)
    private val mutableResponses = MutableSharedFlow<WalletResponse>(extraBufferCapacity = RESPONSE_BUFFER)

    override val state: StateFlow<WalletState> = mutableState.asStateFlow()
    override val responses: SharedFlow<WalletResponse> = mutableResponses.asSharedFlow()

    init {
        // Subscribed before this constructor returns, so no event after it is missed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { client.events.collect { onEvent(it) } }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { bridge.closed.collect { onPickerClosed() } }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bridge.failed.collect { mutableState.compareAndSet(WalletState.Connecting, WalletState.Failed(WalletFailure.Unknown)) }
        }
        scope.launch { restore() }
    }

    private suspend fun restore() {
        val address = withContext(io) { client.accountAddress() }?.takeIf { Hex.isAddress(it) } ?: return
        // A session approved while the lookup ran wins over the restored one.
        mutableState.compareAndSet(WalletState.Disconnected, WalletState.Connected(address.lowercase()))
    }

    override fun openPicker() {
        mutableState.value = WalletState.Connecting
        bridge.request()
    }

    override suspend fun sendRequest(request: WalletRequest): Result<RequestHandle> {
        val (method, params) = when (request) {
            is WalletRequest.PersonalSign -> "personal_sign" to WalletRequestParams.personalSign(request.message, request.address)
            is WalletRequest.SendTransaction -> "eth_sendTransaction" to WalletRequestParams.sendTransaction(request)
        }
        val id = suspendCancellableCoroutine<Result<Long>> { cont ->
            client.request(
                method = method,
                paramsJson = params,
                chainId = WalletRequestParams.CHAIN_ID,
                onSent = { requestId -> if (cont.isActive) cont.resume(Result.success(requestId)) },
                onError = { error -> if (cont.isActive) cont.resume(Result.failure(error)) },
            )
        }
        return id.map { RequestHandle(it) }
    }

    override fun openWalletApp() {
        scope.launch {
            val link = withContext(io) { client.activeSessionRedirect() }
            if (link != null && isSafeLink(link)) launcher.open(link)
        }
    }

    override suspend fun disconnect() {
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                client.disconnect(
                    onDone = { if (cont.isActive) cont.resume(Unit) },
                    onError = { if (cont.isActive) cont.resume(Unit) },
                )
            }
        } finally {
            mutableState.value = WalletState.Disconnected
        }
    }

    private suspend fun onEvent(event: AppKitEvent) {
        when (event) {
            is AppKitEvent.SessionApproved -> onApproved(event.address)
            AppKitEvent.SessionDeleted -> mutableState.value = WalletState.Disconnected
            // These two only matter while the person is connecting. A relay hiccup must not undo a good session.
            AppKitEvent.SessionRejected ->
                mutableState.compareAndSet(WalletState.Connecting, WalletState.Failed(WalletFailure.Declined))
            AppKitEvent.ConnectionError ->
                mutableState.compareAndSet(WalletState.Connecting, WalletState.Failed(WalletFailure.Unknown))
            is AppKitEvent.ResponseResult -> publish(event.requestId, classify(event.result))
            is AppKitEvent.ResponseError -> publish(event.requestId, classify(event.code, event.message))
            is AppKitEvent.RequestExpired -> publish(event.requestId, WalletOutcome.Failed(EXPIRED_CODE, EXPIRED_MESSAGE))
        }
    }

    private suspend fun onApproved(approved: String?) {
        val address = approved?.takeIf { Hex.isAddress(it) }
            ?: withContext(io) { client.accountAddress() }?.takeIf { Hex.isAddress(it) }
        mutableState.value = if (address != null) {
            WalletState.Connected(address.lowercase())
        } else {
            WalletState.Failed(WalletFailure.Unknown)
        }
    }

    private suspend fun onPickerClosed() {
        // The approval can land just after the picker closes, so wait a moment before giving up.
        delay(pickerCloseGraceMillis)
        mutableState.compareAndSet(WalletState.Connecting, WalletState.Disconnected)
    }

    private suspend fun publish(requestId: Long, outcome: WalletOutcome) {
        mutableResponses.emit(WalletResponse(requestId, outcome))
    }

    internal companion object {
        const val RESPONSE_BUFFER = 64
        const val PICKER_CLOSE_GRACE_MILLIS = 1_500L
        const val BAD_RESULT_CODE = -1
        const val EXPIRED_CODE = -2
        const val EXPIRED_MESSAGE = "The request expired"

        private val SIGNATURE = Regex("^0x[0-9a-fA-F]{130}$")

        // EIP-1193 4900 and 4901 (disconnected), WalletConnect 6000 (user disconnected) and 8000 (session expired).
        private val SESSION_CODES = setOf(4900, 4901, 6000, 8000)
        private val REJECTED_CODES = setOf(4001, 5000)

        // Schemes that must never be started from a link a remote wallet supplied. The first group can
        // start another component, the second one reaches the dialer, messages, maps, the store or
        // a plain web page. Wallets use https links or their own scheme (`metamask://`), nothing else.
        private val BLOCKED_SCHEMES = setOf(
            "intent", "file", "content", "javascript", "android-app", "data",
            "http", "tel", "sms", "smsto", "mms", "mmsto", "mailto", "geo", "market", "sip", "sips",
            "ftp", "ws", "wss", "about", "blob", "vbscript", "tel-ussd",
        )
        private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

        fun classify(result: String): WalletOutcome = when {
            SIGNATURE.matches(result) -> WalletOutcome.Signature(result)
            Hex.isTxHash(result) -> WalletOutcome.TransactionHash(result)
            else -> WalletOutcome.Failed(BAD_RESULT_CODE, "The wallet answered with an unexpected result")
        }

        fun classify(code: Int, message: String): WalletOutcome = when (code) {
            in REJECTED_CODES -> WalletOutcome.Rejected
            in SESSION_CODES -> WalletOutcome.SessionLost
            else -> WalletOutcome.Failed(code, message)
        }

        /**
         * An https link needs a host and no user info (`https://a.com@evil.com`), the same rule as
         * the in-app browser. Any other scheme has to be a wallet's own custom scheme, so every
         * scheme in [BLOCKED_SCHEMES] is refused, plain http included.
         */
        fun isSafeLink(link: String): Boolean {
            val scheme = SCHEME.find(link)?.groupValues?.get(1)?.lowercase() ?: return false
            if (scheme == "https") return isHttpsWithHost(link)
            return scheme !in BLOCKED_SCHEMES
        }

        private fun isHttpsWithHost(link: String): Boolean {
            val uri = try {
                URI(link)
            } catch (_: URISyntaxException) {
                return false
            }
            return !uri.host.isNullOrEmpty() && uri.userInfo == null
        }
    }
}

/**
 * The parameters of the two wallet methods, built with JSON builders and never with string
 * templates. WalletConnect orders `personal_sign` as `[message, address]`, with the message as `0x`
 * plus its UTF-8 bytes. The transaction object carries no fee field: the wallet estimates and shows it.
 */
internal object WalletRequestParams {
    /** Polygon Amoy, in the CAIP-2 form the wallet expects. */
    const val CHAIN_ID = "eip155:80002"

    fun personalSign(message: String, address: String): String =
        buildJsonArray {
            add(JsonPrimitive(Hex.utf8ToHex(message)))
            add(JsonPrimitive(address))
        }.toString()

    fun sendTransaction(request: WalletRequest.SendTransaction): String =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("from", request.from)
                    put("to", request.to)
                    put("data", request.data)
                    put("value", request.value)
                },
            )
        }.toString()
}
