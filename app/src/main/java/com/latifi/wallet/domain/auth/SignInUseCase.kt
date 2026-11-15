package com.latifi.wallet.domain.auth

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.wallet.RequestHandle
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** How one sign-in run ended. No input is lost on a failure: a retry starts from the challenge. */
sealed interface SignInResult {
    data object Signed : SignInResult

    /** The person said no in the wallet. */
    data object WalletDeclined : SignInResult

    data object WalletSessionLost : SignInResult

    /** A 401 on challenge or verify. */
    data object Invalid : SignInResult

    data class RateLimited(val retryAfterSeconds: Int?) : SignInResult

    data object NetworkError : SignInResult
}

/**
 * Challenge, wallet signature, verify. The message goes to the wallet exactly as the server
 * returned it: never trimmed, never re-encoded, never built by the app. Only the answer whose
 * request id equals the handle this run received is accepted.
 *
 * The run waits for the wallet without a time limit. The 15 second panel belongs to the screen,
 * and "Keep waiting" only restarts that panel, so a late signature still completes the run.
 */
class SignInUseCase @Inject constructor(
    private val wallet: WalletGateway,
    private val auth: AuthRepository,
    private val sessions: SessionStore,
    private val clock: Clock,
) {
    suspend fun signIn(address: String): SignInResult {
        val challenge = auth.challenge(address).getOrElse { return it.toResult() }

        return coroutineScope {
            // Subscribe before asking: the wallet flow has no replay, and a fast wallet could
            // answer before this run starts to listen.
            val inbox = Channel<WalletResponse>(Channel.UNLIMITED)
            val listener = launch(start = CoroutineStart.UNDISPATCHED) {
                wallet.responses.collect { inbox.send(it) }
            }
            try {
                val handle: RequestHandle = wallet
                    .sendRequest(WalletRequest.PersonalSign(message = challenge.message, address = address))
                    .getOrElse { return@coroutineScope it.rethrowCancellation().let { SignInResult.WalletSessionLost } }

                val outcome = awaitOutcome(inbox, handle.requestId)
                when (outcome) {
                    is WalletOutcome.Signature -> verify(challenge, address, outcome.hex)
                    WalletOutcome.Rejected -> SignInResult.WalletDeclined
                    WalletOutcome.SessionLost -> SignInResult.WalletSessionLost
                    is WalletOutcome.Failed -> SignInResult.WalletSessionLost
                    is WalletOutcome.TransactionHash -> SignInResult.WalletSessionLost
                }
            } finally {
                listener.cancel()
                inbox.close()
            }
        }
    }

    private suspend fun awaitOutcome(inbox: Channel<WalletResponse>, requestId: Long): WalletOutcome {
        while (true) {
            val response = inbox.receive()
            if (response.requestId == requestId) return response.outcome
        }
    }

    private suspend fun verify(challenge: Challenge, address: String, signature: String): SignInResult {
        val token = auth.verify(challenge.challengeId, address, signature).getOrElse { return it.toResult() }
        sessions.save(
            Session(
                address = token.address,
                token = token.accessToken,
                expiresAt = clock.instant().plus(Duration.ofSeconds(token.expiresInSeconds)),
            ),
        )
        return SignInResult.Signed
    }

    private fun Throwable.rethrowCancellation(): Throwable {
        if (this is CancellationException) throw this
        return this
    }

    private fun Throwable.toResult(): SignInResult {
        if (this is CancellationException) throw this
        return when (val error = (this as? AppException)?.error) {
            AppError.Unauthorized -> SignInResult.Invalid
            is AppError.RateLimited -> SignInResult.RateLimited(error.retryAfterSeconds)
            else -> SignInResult.NetworkError
        }
    }
}
