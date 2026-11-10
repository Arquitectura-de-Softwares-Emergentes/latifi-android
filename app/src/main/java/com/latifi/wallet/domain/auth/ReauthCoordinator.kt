package com.latifi.wallet.domain.auth

import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SignInReason { FirstSignIn, SessionExpired }

sealed interface SignInOutcome {
    data object Signed : SignInOutcome

    data object Cancelled : SignInOutcome
}

/** One sign-in run that the sheet has to show. [id] changes with every run. */
data class SignInRequest(val id: Long, val reason: SignInReason)

/**
 * Serves the first sign-in and every later 401. Parallel callers share one sheet and one
 * signature: while a run is open, [ensureSession] joins it instead of starting another.
 */
interface ReauthCoordinator {
    /** The run the sheet shows, or null when no sign-in is open. */
    val request: StateFlow<SignInRequest?>

    suspend fun ensureSession(reason: SignInReason): SignInOutcome

    /** The sheet calls this when its run ends. It closes the sheet and wakes every waiting caller. */
    suspend fun report(outcome: SignInOutcome)
}

class DefaultReauthCoordinator(private val overlays: OverlayController) : ReauthCoordinator {
    private val lock = Mutex()
    private var inFlight: CompletableDeferred<SignInOutcome>? = null
    private var nextId = 0L
    private val mutableRequest = MutableStateFlow<SignInRequest?>(null)

    override val request: StateFlow<SignInRequest?> = mutableRequest.asStateFlow()

    /** How many runs were started. A test reads it to prove two callers shared one run. */
    var runs: Int = 0
        private set

    override suspend fun ensureSession(reason: SignInReason): SignInOutcome {
        val (deferred, owner) = lock.withLock {
            val running = inFlight
            if (running != null) {
                running to false
            } else {
                val fresh = CompletableDeferred<SignInOutcome>()
                inFlight = fresh
                runs += 1
                nextId += 1
                mutableRequest.value = SignInRequest(nextId, reason)
                fresh to true
            }
        }
        if (owner) overlays.show(Overlays.SIGN_IN)
        return deferred.await()
    }

    override suspend fun report(outcome: SignInOutcome) {
        val finished = lock.withLock {
            val running = inFlight
            inFlight = null
            mutableRequest.value = null
            running
        }
        overlays.dismiss(Overlays.SIGN_IN)
        finished?.complete(outcome)
    }
}
