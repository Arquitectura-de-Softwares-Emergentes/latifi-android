package com.latifi.wallet.ui.tx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.latifi.wallet.R
import com.latifi.wallet.core.Hex
import com.latifi.wallet.domain.tx.FailReason
import com.latifi.wallet.domain.tx.FailureActions
import com.latifi.wallet.domain.tx.FailurePrimary
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.ui.components.CustomTabLauncher
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.SecondaryButton
import com.latifi.wallet.ui.components.StepProgress
import com.latifi.wallet.ui.components.SuccessBadge
import com.latifi.wallet.ui.components.rememberHaptics
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.Spacing

private val StatusIconSize = 24.dp
private const val FocusAttempts = 5

/** The sheet cannot be dragged or tapped away while a transaction is in flight. A finished one can. */
fun canDismissByGesture(state: TxState): Boolean =
    state is TxState.Confirmed || state is TxState.Failed || state is TxState.Idle

@StringRes
private fun actionName(action: TxAction): Int = when (action) {
    TxAction.Coins -> R.string.tx_action_coins
    TxAction.Request -> R.string.tx_action_request
    TxAction.Payment -> R.string.tx_action_payment
    TxAction.Funding -> R.string.tx_action_funding
}

@StringRes
private fun failureCopy(reason: FailReason): Int = when (reason) {
    FailReason.Declined -> R.string.tx_fail_declined
    FailReason.Session, FailReason.Timeout -> R.string.tx_fail_session
    FailReason.Fee -> R.string.tx_fail_fee
    FailReason.Tokens -> R.string.tx_fail_tokens
    FailReason.Reverted -> R.string.tx_fail_reverted
    FailReason.NotOpen -> R.string.tx_fail_notopen
    FailReason.Network -> R.string.tx_fail_network
}

private enum class Progress { None, Circular, Linear }

private data class Look(
    val container: Color?,
    @DrawableRes val icon: Int,
    val iconTint: Color,
    val progress: Progress,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

@Composable
private fun lookOf(state: TxState): Look {
    val colors = LatiFiTheme.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    return when (state) {
        is TxState.Pending, TxState.Idle -> Look(
            null, R.drawable.ic_hourglass_top, onSurface, Progress.Circular,
            R.string.tx_pending_title, R.string.tx_pending_body,
        )
        TxState.PendingTimeout -> Look(
            colors.warningContainer, R.drawable.ic_warning, onSurface, Progress.None,
            R.string.tx_timeout_title, R.string.tx_timeout_body,
        )
        is TxState.Confirming -> Look(
            null, R.drawable.ic_sync, onSurface, Progress.Linear,
            R.string.tx_confirming_title, R.string.tx_confirming_body,
        )
        // The title stays "Confirming": a delay is never a failure, so the look stays a warning, not an error.
        is TxState.ConfirmingSlow, TxState.Reconciling -> Look(
            colors.warningContainer, R.drawable.ic_sync, onSurface, Progress.Linear,
            R.string.tx_confirming_title, R.string.tx_slow,
        )
        is TxState.Confirmed -> Look(
            colors.successContainer, R.drawable.ic_check_circle, colors.success, Progress.None,
            R.string.tx_confirmed_title, R.string.tx_confirmed_body,
        )
        is TxState.Failed -> Look(
            MaterialTheme.colorScheme.errorContainer, R.drawable.ic_error, MaterialTheme.colorScheme.error,
            Progress.None, R.string.tx_failed_title, failureCopy(state.reason),
        )
    }
}

/** A key that changes whenever the state changes in a way that offers a new set of actions. */
private fun focusKey(state: TxState): Any = when (state) {
    is TxState.Failed -> "failed-${state.reason}"
    else -> state::class
}

/**
 * S19 content, stateless so a test can show every state. The pane title is the action name. The
 * status title is the only live node: polite, assertive for Failed, and it announces only when the
 * title changes, so the slow notice that keeps the title "Confirming" says nothing new. After each
 * state that offers an action the primary button takes focus. Words never say "hash": the
 * transaction reference is a receipt.
 */
@Composable
fun TxStatusSheetContent(
    ui: TxStatusUiState,
    onEvent: (TxStatusEvent) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes notice: Int? = null,
    cancelFocus: FocusRequester = remember { FocusRequester() },
) {
    val state = ui.state
    val context = ui.context ?: return
    val action = stringResource(actionName(context.action))
    val primaryFocus = remember { FocusRequester() }
    val look = lookOf(state)
    val offersAction = state !is TxState.Confirming
    RequestFocusOnChange(key = focusKey(state), enabled = offersAction, requester = primaryFocus)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = action }
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (context.stepCount >= 2) StepProgress(step = context.stepIndex, total = context.stepCount)

        StatusBlock(state = state, look = look)

        // The approve step says what it allows: the exact amount and nothing else.
        val allowAmount = context.allowAmountText
        if (allowAmount != null && state !is TxState.Confirmed && state !is TxState.Failed) {
            AllowDetail(allowAmount)
        }

        when (state) {
            is TxState.Idle, is TxState.Pending -> {
                PrimaryButton(
                    text = stringResource(R.string.tx_open_wallet),
                    onClick = { onEvent(TxStatusEvent.OpenWallet) },
                    modifier = Modifier.focusRequester(primaryFocus),
                )
                CancelButton(action, cancelFocus, onEvent)
            }
            TxState.PendingTimeout -> {
                PrimaryButton(
                    text = stringResource(R.string.tx_reconnect),
                    onClick = { onEvent(TxStatusEvent.Reconnect) },
                    modifier = Modifier.focusRequester(primaryFocus),
                )
                SecondaryButton(
                    text = stringResource(R.string.tx_keep_waiting),
                    onClick = { onEvent(TxStatusEvent.KeepWaiting) },
                )
                CancelButton(action, cancelFocus, onEvent)
            }
            is TxState.Confirming -> BackToHomeButton(onEvent, primary = false, focus = primaryFocus)
            is TxState.ConfirmingSlow -> {
                PrimaryButton(
                    text = stringResource(R.string.tx_keep_waiting_confirm),
                    onClick = { onEvent(TxStatusEvent.KeepWaitingConfirmation) },
                    modifier = Modifier.focusRequester(primaryFocus),
                )
                BackToHomeButton(onEvent, primary = false, focus = primaryFocus)
            }
            TxState.Reconciling -> BackToHomeButton(onEvent, primary = true, focus = primaryFocus)
            is TxState.Confirmed -> ConfirmedActions(state, ui, onEvent, primaryFocus)
            is TxState.Failed -> FailedActions(state, ui, action, cancelFocus, primaryFocus, onEvent)
        }

        if (notice != null) {
            Text(
                text = stringResource(notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun StatusBlock(state: TxState, look: Look) {
    val titleRegion = if (state is TxState.Failed) LiveRegionMode.Assertive else LiveRegionMode.Polite
    val haptics = rememberHaptics()
    LaunchedEffect(state is TxState.Failed) { if (state is TxState.Failed) haptics.reject() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (look.container != null) Modifier.background(look.container, LatiFiShapes.card) else Modifier)
            .padding(if (look.container != null) Spacing.lg else 0.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (look.progress == Progress.Circular) {
                CircularProgressIndicator(modifier = Modifier.size(StatusIconSize), strokeWidth = 3.dp)
            }
            if (state is TxState.Confirmed) {
                // The confirmation moment: a disc, a check that draws itself and a small burst.
                SuccessBadge(size = 40.dp)
            } else {
                Icon(
                    painter = painterResource(look.icon),
                    contentDescription = null,
                    tint = look.iconTint,
                    modifier = Modifier.size(StatusIconSize),
                )
            }
            Text(
                text = stringResource(look.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = titleRegion
                },
            )
        }
        if (look.progress == Progress.Linear) {
            // The progress is decoration: the live title already says what is going on.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
        }
        Text(
            text = stringResource(look.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AllowDetail(amount: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.tx_allow_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.tx_allow_body, amount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CancelButton(action: String, focus: FocusRequester, onEvent: (TxStatusEvent) -> Unit) {
    TertiaryButton(
        text = stringResource(R.string.tx_cancel, action),
        onClick = { onEvent(TxStatusEvent.Cancel) },
        modifier = Modifier.focusRequester(focus),
        fillMaxWidth = true,
    )
}

@Composable
private fun BackToHomeButton(onEvent: (TxStatusEvent) -> Unit, primary: Boolean, focus: FocusRequester) {
    if (primary) {
        PrimaryButton(
            text = stringResource(R.string.back_to_home),
            onClick = { onEvent(TxStatusEvent.BackToHome) },
            modifier = Modifier.focusRequester(focus),
        )
    } else {
        TertiaryButton(
            text = stringResource(R.string.back_to_home),
            onClick = { onEvent(TxStatusEvent.BackToHome) },
            fillMaxWidth = true,
        )
    }
}

@Composable
private fun ConfirmedActions(
    state: TxState.Confirmed,
    ui: TxStatusUiState,
    onEvent: (TxStatusEvent) -> Unit,
    primaryFocus: FocusRequester,
) {
    val hash = state.hash?.takeIf { Hex.isTxHash(it) }
    val ctx = ui.context
    val successTitle = ctx?.successTitleRes
    if (successTitle != null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(successTitle),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            ctx.successBodyRes?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (hash != null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.receipt_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = Hex.shortReference(hash), style = MaterialTheme.typography.titleMedium)
        }
    }
    val primary = ui.context?.confirmedPrimary
    PrimaryButton(
        text = stringResource(primary?.labelRes ?: R.string.back_to_home),
        onClick = { onEvent(TxStatusEvent.ConfirmedPrimary) },
        modifier = Modifier.focusRequester(primaryFocus),
    )
    if (hash != null) {
        SecondaryButton(text = stringResource(R.string.receipt_copy), onClick = { onEvent(TxStatusEvent.CopyReceipt) })
        TertiaryButton(
            text = stringResource(R.string.receipt_view),
            onClick = { onEvent(TxStatusEvent.ViewReceipt) },
            fillMaxWidth = true,
        )
    }
}

@Composable
private fun FailedActions(
    state: TxState.Failed,
    ui: TxStatusUiState,
    action: String,
    cancelFocus: FocusRequester,
    primaryFocus: FocusRequester,
    onEvent: (TxStatusEvent) -> Unit,
) {
    var primary = FailureActions.primaryFor(state.reason)
    // Without a connected wallet there is no address to copy, so the way forward is to reconnect.
    if (primary == FailurePrimary.CopyWalletAddress && ui.walletAddress == null) primary = FailurePrimary.ReconnectWallet
    val (label, event) = when (primary) {
        FailurePrimary.Retry -> stringResource(R.string.tx_retry, action) to TxStatusEvent.Retry
        FailurePrimary.ReconnectWallet -> stringResource(R.string.tx_reconnect) to TxStatusEvent.Reconnect
        FailurePrimary.CopyWalletAddress -> stringResource(R.string.tx_copy_address) to TxStatusEvent.CopyWalletAddress
        FailurePrimary.GetTestCoins -> stringResource(R.string.coins_cta) to TxStatusEvent.GetTestCoins
        FailurePrimary.BackToRequests -> stringResource(R.string.back_to_requests) to TxStatusEvent.BackToRequests
    }
    PrimaryButton(text = label, onClick = { onEvent(event) }, modifier = Modifier.focusRequester(primaryFocus))
    CancelButton(action, cancelFocus, onEvent)
}

/**
 * Gives focus to the primary action when the state changes. The sheet window takes focus a frame
 * or two after it opens, so one request can be refused. It tries again on the next frames, at most
 * [FocusAttempts] times, and stops as soon as the focus is held.
 */
@Composable
private fun RequestFocusOnChange(key: Any, enabled: Boolean, requester: FocusRequester) {
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        repeat(FocusAttempts) {
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
}

/**
 * The bottom sheet around [TxStatusSheetContent]: no drag handle, and no swipe or tap outside
 * while the transaction is in flight. Back and a tap outside go to [TxStatusEvent.Back], which
 * moves focus to Cancel in Pending, hides the sheet in Confirming and closes a finished one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxStatusSheetHost(
    ui: TxStatusUiState,
    onEvent: (TxStatusEvent) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes notice: Int? = null,
    cancelFocus: FocusRequester = remember { FocusRequester() },
) {
    val dismissible by rememberUpdatedState(canDismissByGesture(ui.state))
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || dismissible },
    )
    ModalBottomSheet(
        onDismissRequest = { onEvent(TxStatusEvent.Back) },
        modifier = modifier,
        sheetState = sheetState,
        shape = LatiFiShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        TxStatusSheetContent(ui = ui, onEvent = onEvent, notice = notice, cancelFocus = cancelFocus)
    }
}

/** The overlay of [com.latifi.wallet.ui.nav.Overlays.TX_STATUS]: it draws nothing while no transaction is active. */
@Composable
fun TxStatusOverlay() {
    val viewModel: TxStatusViewModel = hiltViewModel()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cancelFocus = remember { FocusRequester() }
    var notice by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TxStatusEffect.CopyText -> {
                    copyText(context, effect.text)
                    notice = effect.notice
                }
                is TxStatusEffect.OpenUrl -> CustomTabLauncher.open(context, effect.url)
                TxStatusEffect.FocusCancel -> runCatching { cancelFocus.requestFocus() }
            }
        }
    }
    // The announcement belongs to the state it was made in.
    LaunchedEffect(focusKey(ui.state)) { notice = null }

    if (ui.state == TxState.Idle || ui.context == null) return
    TxStatusSheetHost(ui = ui, onEvent = viewModel::onEvent, notice = notice, cancelFocus = cancelFocus)
}

/** The wallet address and the transaction reference are public chain data, so they go on the clipboard as plain text. */
private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("latifi", text))
}
