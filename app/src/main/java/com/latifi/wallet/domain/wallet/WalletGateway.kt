package com.latifi.wallet.domain.wallet

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only door between the app and the user's wallet. Screens and use cases depend on this
 * interface, so the fake wallet of the demo and the live wallet of plan 05-14 are one binding.
 *
 * A request is asked in two steps. [sendRequest] returns a [RequestHandle] as soon as the wallet
 * has been asked, and the answer arrives later on [responses] with the same request id. The
 * request id exists before any answer does, so a caller can store it and match a late answer
 * after the app was killed and came back.
 */
interface WalletGateway {
    val state: StateFlow<WalletState>

    /** Process-wide answers of the wallet. No replay: a collector must be subscribed before it asks. */
    val responses: SharedFlow<WalletResponse>

    /** Opens the wallet picker. The result shows up on [state]. */
    fun openPicker()

    suspend fun sendRequest(request: WalletRequest): Result<RequestHandle>

    /** Brings the wallet app to the front without sending a new request. */
    fun openWalletApp()

    suspend fun disconnect()
}

sealed interface WalletState {
    /** The build has no wallet project id. The connect button explains why it is off. */
    data object NotConfigured : WalletState

    data object Disconnected : WalletState

    data object Connecting : WalletState

    data class Connected(val address: String) : WalletState

    data class Failed(val kind: WalletFailure) : WalletState
}

enum class WalletFailure { NotInstalled, Declined, Timeout, Unknown }

sealed interface WalletRequest {
    /** An EIP-191 personal message. [message] is signed exactly as given. */
    data class PersonalSign(val message: String, val address: String) : WalletRequest

    data class SendTransaction(val from: String, val to: String, val data: String, val value: String) : WalletRequest
}

/** The id the wallet layer gave to one request. */
@JvmInline
value class RequestHandle(val requestId: Long)

data class WalletResponse(val requestId: Long, val outcome: WalletOutcome)

sealed interface WalletOutcome {
    data class Signature(val hex: String) : WalletOutcome

    data class TransactionHash(val hex: String) : WalletOutcome

    /** The person said no in the wallet. */
    data object Rejected : WalletOutcome

    data class Failed(val code: Int, val message: String) : WalletOutcome

    /** The wallet connection ended before it answered. */
    data object SessionLost : WalletOutcome
}
