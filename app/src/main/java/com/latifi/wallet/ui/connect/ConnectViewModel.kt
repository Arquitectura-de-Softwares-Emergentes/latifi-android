package com.latifi.wallet.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.auth.SignInOutcome
import com.latifi.wallet.domain.auth.SignInReason
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.PostSignInUseCase
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.ui.overlay.OverlayController
import com.latifi.wallet.ui.nav.Overlays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class ConnectPhase { Idle, Waiting, Failed, NotConfigured }

/** [returning] picks the title "Reconnect your wallet" instead of "Connect your wallet". */
data class ConnectUiState(val phase: ConnectPhase = ConnectPhase.Idle, val returning: Boolean = false)

sealed interface ConnectEvent {
    /** The primary button, and "Retry connection" in the failure panel. */
    data object Connect : ConnectEvent

    data object NoWallet : ConnectEvent

    data object OpenTerms : ConnectEvent

    data object OpenPrivacy : ConnectEvent
}

sealed interface ConnectEffect {
    data object GoToRole : ConnectEffect

    data object GoToMain : ConnectEffect

    data class OpenLink(val url: String) : ConnectEffect
}

/**
 * S02. Opens the wallet picker, and when the wallet reports Connected it asks the coordinator for
 * the first sign-in, once. A Signed outcome moves on to role selection, or straight to the tabs
 * when a role is already stored. A cancelled sign-in keeps the wallet connected, and the primary
 * button then runs the sign-in again without opening the picker.
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val wallet: WalletGateway,
    private val reauth: ReauthCoordinator,
    private val prefs: PrefsStore,
    private val overlays: OverlayController,
    private val config: AppConfig,
    private val sessions: SessionStore,
    private val postSignIn: PostSignInUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ConnectUiState())
    private val effects = Channel<ConnectEffect>(Channel.BUFFERED)
    private var signInStarted = false
    private var returning = false

    val state: StateFlow<ConnectUiState> = mutableState.asStateFlow()
    val effect = effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            wallet.state.collect { walletState -> onWalletState(walletState) }
        }
    }

    fun setReturning(returning: Boolean) {
        this.returning = returning
        mutableState.value = mutableState.value.copy(returning = returning)
    }

    fun onEvent(event: ConnectEvent) {
        when (event) {
            ConnectEvent.Connect -> connect()
            ConnectEvent.NoWallet -> overlays.show(Overlays.WALLET_HELP)
            ConnectEvent.OpenTerms -> effects.trySend(ConnectEffect.OpenLink(config.termsUrl))
            ConnectEvent.OpenPrivacy -> effects.trySend(ConnectEffect.OpenLink(config.privacyUrl))
        }
    }

    private fun connect() {
        val current = wallet.state.value
        if (current is WalletState.Connected) {
            startSignIn()
        } else {
            signInStarted = false
            wallet.openPicker()
        }
    }

    private fun onWalletState(walletState: WalletState) {
        val phase = when (walletState) {
            WalletState.NotConfigured -> ConnectPhase.NotConfigured
            WalletState.Disconnected -> ConnectPhase.Idle
            WalletState.Connecting -> ConnectPhase.Waiting
            is WalletState.Failed -> ConnectPhase.Failed
            is WalletState.Connected -> ConnectPhase.Waiting
        }
        mutableState.value = mutableState.value.copy(phase = phase)
        if (walletState is WalletState.Connected) startSignIn()
        if (walletState !is WalletState.Connected) signInStarted = false
    }

    /** One sign-in per connection: the flag blocks a second run until the wallet reconnects. */
    private fun startSignIn() {
        if (signInStarted) return
        signInStarted = true
        viewModelScope.launch {
            when (reauth.ensureSession(SignInReason.FirstSignIn)) {
                SignInOutcome.Signed -> {
                    // A returning person's profile loads silently here and queues the greeting for
                    // Home. The wait is capped, so a slow network never holds the person on Connect.
                    sessions.current()?.address?.let { address ->
                        withTimeoutOrNull(PROFILE_LOAD_LIMIT_MILLIS) { postSignIn.run(address) }
                    }
                    val hasRole = prefs.role.first() != null
                    effects.send(if (hasRole) ConnectEffect.GoToMain else ConnectEffect.GoToRole)
                }
                SignInOutcome.Cancelled -> {
                    // The wallet stays connected. The next tap on the primary button signs in again.
                    signInStarted = false
                    mutableState.value = mutableState.value.copy(phase = ConnectPhase.Idle)
                }
            }
        }
    }

    private companion object {
        const val PROFILE_LOAD_LIMIT_MILLIS = 2_000L
    }
}
