package com.latifi.wallet.data.wallet

import kotlinx.coroutines.flow.SharedFlow

/**
 * A thin door over the static AppKit API, so the wallet gateway can be tested on the JVM with a
 * fake. Only the real implementation touches the Reown classes. Nothing here carries a key: the
 * wallet app holds the keys and signs every request.
 */
interface AppKitClient {
    /** Process-wide events from the wallet SDK. No replay: the gateway subscribes at construction. */
    val events: SharedFlow<AppKitEvent>

    /** Registers the SDK delegate that feeds [events]. Safe to call again. Does nothing before the SDK started. */
    fun attachDelegate()

    /** The connected account, or null. It blocks, so callers run it on an IO dispatcher. */
    fun accountAddress(): String?

    /**
     * Asks the wallet. [onSent] gets the request id as soon as the wallet has been asked. The answer
     * arrives later on [events]. The SDK opens the wallet app itself after a successful send.
     */
    fun request(method: String, paramsJson: String, chainId: String, onSent: (Long) -> Unit, onError: (Throwable) -> Unit)

    /** The deep link of the wallet of the active session, or null. It blocks, so callers run it on an IO dispatcher. */
    fun activeSessionRedirect(): String?

    fun disconnect(onDone: () -> Unit, onError: (Throwable) -> Unit)

    /** Renews the wallet session when there is one. Errors are swallowed: an old session just stays as it is. */
    suspend fun extendSession()
}

/** What the SDK delegate reports, reduced to the cases the app reacts to. */
sealed interface AppKitEvent {
    /** The wallet approved a session. [address] is null when the approval carried no readable account. */
    data class SessionApproved(val address: String?) : AppKitEvent

    data object SessionRejected : AppKitEvent

    data object SessionDeleted : AppKitEvent

    data object ConnectionError : AppKitEvent

    data class ResponseResult(val requestId: Long, val result: String) : AppKitEvent

    data class ResponseError(val requestId: Long, val code: Int, val message: String) : AppKitEvent

    data class RequestExpired(val requestId: Long) : AppKitEvent
}
