package com.latifi.wallet.ui.tx

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.R
import com.latifi.wallet.core.Hex
import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.domain.tx.TransactionCoordinator
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the sheet draws: the state, the flow's context and the wallet address a Fee failure can copy. */
data class TxStatusUiState(
    val state: TxState = TxState.Idle,
    val context: TxContext? = null,
    val walletAddress: String? = null,
)

sealed interface TxStatusEvent {
    data object OpenWallet : TxStatusEvent

    data object KeepWaiting : TxStatusEvent

    data object Reconnect : TxStatusEvent

    /** "Cancel {action}". */
    data object Cancel : TxStatusEvent

    data object Retry : TxStatusEvent

    data object KeepWaitingConfirmation : TxStatusEvent

    data object BackToHome : TxStatusEvent

    data object BackToRequests : TxStatusEvent

    data object GetTestCoins : TxStatusEvent

    data object CopyWalletAddress : TxStatusEvent

    data object CopyReceipt : TxStatusEvent

    data object ViewReceipt : TxStatusEvent

    /** The button the flow supplied for the Confirmed state. */
    data object ConfirmedPrimary : TxStatusEvent

    /** The system Back or a tap outside the sheet. */
    data object Back : TxStatusEvent
}

sealed interface TxStatusEffect {
    /** Copies [text] to the clipboard and announces [notice]. */
    data class CopyText(val text: String, @StringRes val notice: Int) : TxStatusEffect

    data class OpenUrl(val url: String) : TxStatusEffect

    /** Moves focus to the Cancel button: Back in Pending asks the person to choose it. */
    data object FocusCancel : TxStatusEffect
}

/**
 * The glue between the sheet and [TransactionCoordinator]. It holds no state of its own: the
 * coordinator is the single source. "Open wallet" only brings the wallet app forward and never
 * sends a second request, so a person who goes to look for the prompt cannot approve twice.
 */
@HiltViewModel
class TxStatusViewModel @Inject constructor(
    private val coordinator: TransactionCoordinator,
    private val wallet: WalletGateway,
    private val overlays: OverlayController,
) : ViewModel() {
    val state: StateFlow<TxStatusUiState> = combine(
        coordinator.state,
        coordinator.context,
        wallet.state,
    ) { tx, context, walletState ->
        TxStatusUiState(tx, context, (walletState as? WalletState.Connected)?.address)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TxStatusUiState())

    private val effectChannel = Channel<TxStatusEffect>(Channel.BUFFERED)
    val effects: Flow<TxStatusEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: TxStatusEvent) {
        val current = coordinator.state.value
        val context = coordinator.context.value
        when (event) {
            TxStatusEvent.OpenWallet -> wallet.openWalletApp()
            TxStatusEvent.KeepWaiting -> coordinator.keepWaiting()
            TxStatusEvent.Reconnect -> coordinator.reconnect()
            TxStatusEvent.Cancel -> coordinator.cancel()
            TxStatusEvent.Retry -> coordinator.retry()
            TxStatusEvent.KeepWaitingConfirmation -> coordinator.keepWaitingConfirmation()
            TxStatusEvent.BackToHome -> backToHome(context)
            TxStatusEvent.BackToRequests -> {
                coordinator.dismiss()
                context?.onBackToRequests?.invoke()
            }
            TxStatusEvent.GetTestCoins -> {
                coordinator.dismiss()
                overlays.show(Overlays.COINS)
            }
            TxStatusEvent.CopyWalletAddress -> wallet.state.value.let { walletState ->
                (walletState as? WalletState.Connected)?.address?.let {
                    effectChannel.trySend(TxStatusEffect.CopyText(it, R.string.profile_address_copied))
                }
            }
            TxStatusEvent.CopyReceipt -> receiptHash(current)?.let {
                effectChannel.trySend(TxStatusEffect.CopyText(it, R.string.receipt_copied))
            }
            TxStatusEvent.ViewReceipt -> receiptHash(current)?.let {
                effectChannel.trySend(TxStatusEffect.OpenUrl(receiptUrl(it)))
            }
            TxStatusEvent.ConfirmedPrimary -> confirmedPrimary(context)
            TxStatusEvent.Back -> onBack(current, context)
        }
    }

    /** Back in Pending moves focus to Cancel, in Confirming it hides the sheet, and a finished state closes it. */
    private fun onBack(current: TxState, context: TxContext?) {
        when (current) {
            is TxState.Pending, is TxState.PendingTimeout -> effectChannel.trySend(TxStatusEffect.FocusCancel)
            is TxState.Confirming, is TxState.ConfirmingSlow, is TxState.Reconciling -> backToHome(context)
            is TxState.Confirmed, is TxState.Failed, is TxState.Idle -> coordinator.dismiss()
        }
    }

    private fun backToHome(context: TxContext?) {
        if (coordinator.state.value.isInFlight()) {
            // The coordinator keeps tracking. Only the sheet goes away.
            coordinator.hide()
        } else {
            coordinator.dismiss()
        }
        context?.onBackToHome?.invoke()
    }

    private fun confirmedPrimary(context: TxContext?) {
        val primary = context?.confirmedPrimary
        if (primary == null) {
            backToHome(context)
            return
        }
        primary.onClick()
        // A flow that starts its second step has moved the state on. If nothing started, the sheet is done.
        if (coordinator.state.value is TxState.Confirmed) coordinator.dismiss()
    }

    private fun receiptHash(state: TxState): String? =
        (state as? TxState.Confirmed)?.hash?.takeIf { Hex.isTxHash(it) }

    private fun TxState.isInFlight(): Boolean =
        this is TxState.Confirming || this is TxState.ConfirmingSlow || this is TxState.Reconciling

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The explorer page of a transaction. The reference is checked before it gets here. */
        fun receiptUrl(hash: String): String = Deployment.EXPLORER_BASE + "/tx/" + hash
    }
}
