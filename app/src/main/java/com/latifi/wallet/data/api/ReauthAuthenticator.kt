package com.latifi.wallet.data.api

import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.auth.SignInOutcome
import com.latifi.wallet.domain.auth.SignInReason
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.session.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.time.Clock

/**
 * Lazy re-authentication (D-03). The token lives 15 minutes and has no refresh, so a 401 on a
 * call outside `/auth/` asks the [ReauthCoordinator] for a new session, which shows the sign-in
 * sheet and the wallet signs. On success the original request goes out once more with the new
 * token. Four rules keep it from looping or storming:
 * - the auth operations never trigger it, a failed `verifyChallenge` is not a session expiry;
 * - a request that already carries [RETRY_MARKER] is not retried again, the 401 reaches the caller;
 * - the coordinator runs one sign-in at a time, so parallel 401s share one signature;
 * - a 401 for a request that was sent before the session was renewed retries with the renewed
 *   token and asks for no second signature.
 *
 * A cancelled sign-in returns null, and the caller sees `AppError.Unauthorized`.
 */
class ReauthAuthenticator(
    private val coordinator: ReauthCoordinator,
    private val sessions: SessionStore,
    private val clock: Clock,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (AuthInterceptor.isAuthPath(request.url)) return null
        if (request.header(RETRY_MARKER) != null) return null

        val sentToken = request.header(AuthInterceptor.AUTHORIZATION)?.removePrefix("${AuthInterceptor.BEARER} ")
        val renewed = runBlocking { sessions.current() }?.takeIf { it.isUsable() && it.token != sentToken }
        if (renewed != null) return retryWith(request, renewed)

        val outcome = runBlocking { coordinator.ensureSession(SignInReason.SessionExpired) }
        if (outcome != SignInOutcome.Signed) return null
        val session = runBlocking { sessions.current() }?.takeIf { it.isUsable() } ?: return null
        return retryWith(request, session)
    }

    private fun Session.isUsable(): Boolean = expiresAt.isAfter(clock.instant())

    private fun retryWith(request: Request, session: Session): Request =
        request.newBuilder()
            .header(AuthInterceptor.AUTHORIZATION, "${AuthInterceptor.BEARER} ${session.token}")
            .header(RETRY_MARKER, "1")
            .build()

    companion object {
        /** Marks the one retry. Its value carries no meaning, and the API ignores the header. */
        const val RETRY_MARKER = "X-Latifi-Retry"
    }
}
