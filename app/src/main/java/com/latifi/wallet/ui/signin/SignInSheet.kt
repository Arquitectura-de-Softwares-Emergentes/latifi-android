package com.latifi.wallet.ui.signin

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.SecondaryButton
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.Spacing

/**
 * S03 content. Stateless, so a test can show every state. The pane title is "Confirm it is you".
 * Exactly one node announces changes: the status text of the current state, polite for progress
 * and the countdown, assertive for a failure. Cancel is always there and never runs the
 * interrupted action.
 */
@Composable
fun SignInSheet(
    state: SignInUiState,
    onEvent: (SignInEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.signin_title)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        when (state.phase) {
            SignInPhase.Ready -> {
                Text(
                    text = stringResource(if (state.reauth) R.string.signin_reauth_body else R.string.signin_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton(text = stringResource(R.string.signin_cta), onClick = { onEvent(SignInEvent.Confirm) })
            }
            SignInPhase.Waiting -> Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(
                    text = stringResource(R.string.connect_waiting),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            SignInPhase.TimedOut -> {
                WarningPanel()
                PrimaryButton(text = stringResource(R.string.tx_reconnect), onClick = { onEvent(SignInEvent.Reconnect) })
                SecondaryButton(text = stringResource(R.string.tx_keep_waiting), onClick = { onEvent(SignInEvent.KeepWaiting) })
            }
            SignInPhase.Invalid -> {
                FailureText(stringResource(R.string.signin_failed))
                PrimaryButton(text = stringResource(R.string.signin_cta), onClick = { onEvent(SignInEvent.Confirm) })
            }
            SignInPhase.RateLimited -> {
                Text(
                    text = stringResource(R.string.signin_ratelimited, state.secondsLeft),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                PrimaryButton(
                    text = stringResource(R.string.signin_cta),
                    onClick = { onEvent(SignInEvent.Confirm) },
                    enabled = false,
                )
            }
            SignInPhase.Declined -> {
                FailureText(stringResource(R.string.signin_declined))
                PrimaryButton(text = stringResource(R.string.signin_cta), onClick = { onEvent(SignInEvent.Confirm) })
            }
            SignInPhase.SessionLost -> {
                FailureText(stringResource(R.string.signin_session_lost))
                PrimaryButton(text = stringResource(R.string.tx_reconnect), onClick = { onEvent(SignInEvent.Reconnect) })
            }
            SignInPhase.Offline -> {
                FailureText(stringResource(R.string.err_offline_title) + ". " + stringResource(R.string.err_offline_body))
                PrimaryButton(text = stringResource(R.string.signin_cta), onClick = { onEvent(SignInEvent.Confirm) })
            }
        }
        TertiaryButton(
            text = stringResource(R.string.signin_cancel),
            onClick = { onEvent(SignInEvent.Cancel) },
            fillMaxWidth = true,
        )
    }
}

/** The "wallet has not answered" panel: a warning container with the icon, the title and the steps. */
@Composable
private fun WarningPanel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.latifi.wallet.ui.theme.LatiFiTheme.colors.warningContainer, LatiFiShapes.card)
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.tx_timeout_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(text = stringResource(R.string.tx_timeout_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FailureText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

/**
 * The bottom sheet around [SignInSheet]. It cannot be dragged away: the sheet ends through
 * Cancel sign-in, or when the sign-in succeeds. Back and a tap outside count as Cancel. The veto
 * covers only the Hidden target. Vetoing every target also blocks the first show(), and the sheet
 * would never open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheetHost(
    state: SignInUiState,
    onEvent: (SignInEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(SignInEvent.Cancel) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { target -> target != SheetValue.Hidden }),
        shape = LatiFiShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        SignInSheet(state = state, onEvent = onEvent)
    }
}
