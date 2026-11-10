package com.latifi.wallet.data.fake

import com.latifi.wallet.domain.wallet.RequestHandle
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletResponse
import com.latifi.wallet.domain.wallet.WalletState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the fake wallet does when it is asked to sign or send. */
sealed interface FakeWalletScript {
    /** Approves after [delayMillis]. */
    data class AutoApprove(val delayMillis: Long = 400) : FakeWalletScript

    data class Reject(val delayMillis: Long = 400) : FakeWalletScript

    /** Never answers, so the 15 second panel can be seen and tested. */
    data object NeverAnswer : FakeWalletScript

    data class SessionLost(val delayMillis: Long = 400) : FakeWalletScript
}

/**
 * A scriptable wallet for the demo mode and for tests. It holds no key and signs nothing: the
 * "signature" is a fixed fake string of the right shape. The demo address is a public test
 * vector, not a person's address.
 */
class FakeWalletGateway(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    initialState: WalletState = WalletState.Disconnected,
    var script: FakeWalletScript = FakeWalletScript.AutoApprove(),
    var connectDelayMillis: Long = 600,
    val demoAddress: String = DEMO_ADDRESS,
) : WalletGateway {
    private val mutableState = MutableStateFlow(initialState)
    private val mutableResponses = MutableSharedFlow<WalletResponse>(extraBufferCapacity = 64)
    private var nextRequestId = 1L

    /** Every request the app sent, in order. Tests read it. */
    val sentRequests = mutableListOf<Pair<Long, WalletRequest>>()

    var pickerOpened = 0
        private set
    var walletAppOpened = 0
        private set

    override val state: StateFlow<WalletState> = mutableState.asStateFlow()
    override val responses: SharedFlow<WalletResponse> = mutableResponses.asSharedFlow()

    override fun openPicker() {
        pickerOpened += 1
        mutableState.value = WalletState.Connecting
        scope.launch {
            delay(connectDelayMillis)
            mutableState.value = WalletState.Connected(demoAddress)
        }
    }

    override suspend fun sendRequest(request: WalletRequest): Result<RequestHandle> {
        val id = nextRequestId++
        sentRequests += id to request
        when (val current = script) {
            is FakeWalletScript.AutoApprove -> answer(id, current.delayMillis, approved(request))
            is FakeWalletScript.Reject -> answer(id, current.delayMillis, WalletOutcome.Rejected)
            is FakeWalletScript.SessionLost -> answer(id, current.delayMillis, WalletOutcome.SessionLost)
            FakeWalletScript.NeverAnswer -> Unit
        }
        return Result.success(RequestHandle(id))
    }

    /** Lets a test answer a request by hand, for example after "Keep waiting". */
    suspend fun respond(requestId: Long, outcome: WalletOutcome) {
        mutableResponses.emit(WalletResponse(requestId, outcome))
    }

    override fun openWalletApp() {
        walletAppOpened += 1
    }

    override suspend fun disconnect() {
        mutableState.value = WalletState.Disconnected
    }

    /** Puts the wallet in any state, for a test or a demo script. */
    fun setState(state: WalletState) {
        mutableState.value = state
    }

    private fun answer(id: Long, delayMillis: Long, outcome: WalletOutcome) {
        scope.launch {
            delay(delayMillis)
            mutableResponses.emit(WalletResponse(id, outcome))
        }
    }

    private fun approved(request: WalletRequest): WalletOutcome = when (request) {
        is WalletRequest.PersonalSign -> WalletOutcome.Signature(FAKE_SIGNATURE)
        is WalletRequest.SendTransaction -> WalletOutcome.TransactionHash(FAKE_TX_HASH)
    }

    companion object {
        const val DEMO_ADDRESS = "0x52908400098527886E0F7030069857D2E4169EE7"
        val FAKE_SIGNATURE = "0x" + "ab".repeat(65)
        val FAKE_TX_HASH = "0x" + "cd".repeat(32)
    }
}
