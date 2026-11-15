package com.latifi.wallet.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.auth.SignInOutcome
import com.latifi.wallet.domain.auth.SignInReason
import com.latifi.wallet.domain.auth.SignInResult
import com.latifi.wallet.domain.auth.SignInUseCase
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.domain.wallet.WalletWaitTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInPhase { Ready, Waiting, TimedOut, Invalid, RateLimited, Declined, SessionLost, Offline }

/** [secondsLeft] only matters in [SignInPhase.RateLimited]. [reauth] picks the body text. */
data class SignInUiState(
    val phase: SignInPhase = SignInPhase.Ready,
    val reauth: Boolean = false,
    val secondsLeft: Int = 0,
)

sealed interface SignInEvent {
    /** "Confirm in wallet", also the retry after a failure. */
    data object Confirm : SignInEvent

    /** "Keep waiting for wallet": restart the 15 second panel, forget nothing. */
    data object KeepWaiting : SignInEvent

    /** "Reconnect wallet": reopen the picker. */
    data object Reconnect : SignInEvent

    /** "Cancel sign-in": return without running the interrupted action. */
    data object Cancel : SignInEvent
}

/**
 * S03. One instance per sign-in run. The run waits for the wallet without a limit, and the
 * [WalletWaitTimer] only decides when to show the "wallet has not answered" panel. A 429
 * disables the button until the Retry-After countdown ends, and the countdown text changes
 * every [TICK_SECONDS] seconds, not every second.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
    private val wallet: WalletGateway,
    private val coordinator: ReauthCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SignInUiState())
    private val timer = WalletWaitTimer(viewModelScope)
    private var runJob: Job? = null
    private var countdownJob: Job? = null
    private var started = false

    val state: StateFlow<SignInUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            timer.state.collect { timerState ->
                if (timerState == WalletWaitTimer.State.TimedOut && mutableState.value.phase == SignInPhase.Waiting) {
                    mutableState.value = mutableState.value.copy(phase = SignInPhase.TimedOut)
                }
            }
        }
    }

    /** Called once by the sheet with the reason of the run. */
    fun start(reason: SignInReason) {
        if (started) return
        started = true
        mutableState.value = SignInUiState(phase = SignInPhase.Ready, reauth = reason == SignInReason.SessionExpired)
    }

    fun onEvent(event: SignInEvent) {
        when (event) {
            SignInEvent.Confirm -> signIn()
            SignInEvent.KeepWaiting -> {
                timer.keepWaiting()
                mutableState.value = mutableState.value.copy(phase = SignInPhase.Waiting)
            }
            SignInEvent.Reconnect -> reconnect()
            SignInEvent.Cancel -> cancel()
        }
    }

    private fun signIn() {
        val connected = wallet.state.value as? WalletState.Connected
        if (connected == null) {
            mutableState.value = mutableState.value.copy(phase = SignInPhase.SessionLost)
            return
        }
        runJob?.cancel()
        countdownJob?.cancel()
        mutableState.value = mutableState.value.copy(phase = SignInPhase.Waiting)
        timer.start()
        runJob = viewModelScope.launch {
            val result = signInUseCase.signIn(connected.address)
            timer.stop()
            when (result) {
                SignInResult.Signed -> coordinator.report(SignInOutcome.Signed)
                SignInResult.WalletDeclined -> setPhase(SignInPhase.Declined)
                SignInResult.WalletSessionLost -> setPhase(SignInPhase.SessionLost)
                SignInResult.Invalid -> setPhase(SignInPhase.Invalid)
                SignInResult.NetworkError -> setPhase(SignInPhase.Offline)
                is SignInResult.RateLimited -> startCountdown(result.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS)
            }
        }
    }

    private fun reconnect() {
        runJob?.cancel()
        timer.stop()
        wallet.openPicker()
        // The picker connects again, and the person confirms in the wallet once more.
        setPhase(SignInPhase.Ready)
    }

    private fun cancel() {
        runJob?.cancel()
        countdownJob?.cancel()
        timer.stop()
        viewModelScope.launch { coordinator.report(SignInOutcome.Cancelled) }
    }

    private fun setPhase(phase: SignInPhase) {
        mutableState.value = mutableState.value.copy(phase = phase)
    }

    private fun startCountdown(totalSeconds: Int) {
        countdownJob?.cancel()
        mutableState.value = mutableState.value.copy(phase = SignInPhase.RateLimited, secondsLeft = totalSeconds)
        countdownJob = viewModelScope.launch {
            var left = totalSeconds
            while (left > 0) {
                val step = minOf(TICK_SECONDS, left)
                delay(step * 1_000L)
                left -= step
                mutableState.value = mutableState.value.copy(secondsLeft = left)
            }
            // The wait is over. The sheet is ready again and the next tap starts from the challenge.
            setPhase(SignInPhase.Ready)
        }
    }

    companion object {
        const val TICK_SECONDS = 5
        const val DEFAULT_RETRY_SECONDS = 30
    }
}
