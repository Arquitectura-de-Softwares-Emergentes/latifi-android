package com.latifi.wallet.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.onboarding.LegalLinks
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.Spacing

/**
 * S02. Everything sits in one scrolling column in reading order: title in the top bar, body,
 * the two buttons, the consent line with its two links, and the test money notice. The failure
 * panel replaces the buttons inside the screen, never as a dialog.
 */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onEvent: (ConnectEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.returning) R.string.connect_title_return else R.string.connect_title
    ScreenScaffold(title = stringResource(title), modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(text = stringResource(R.string.connect_body), style = MaterialTheme.typography.bodyLarge)

            when (state.phase) {
                ConnectPhase.Failed -> FailurePanel(onEvent)
                else -> {
                    if (state.phase == ConnectPhase.Waiting) WaitingStatus()
                    PrimaryButton(
                        text = stringResource(R.string.connect_cta),
                        onClick = { onEvent(ConnectEvent.Connect) },
                        enabled = state.phase == ConnectPhase.Idle,
                        disabledReason = if (state.phase == ConnectPhase.NotConfigured) {
                            stringResource(R.string.wallet_not_configured)
                        } else {
                            null
                        },
                    )
                    TertiaryButton(
                        text = stringResource(R.string.connect_nowallet),
                        onClick = { onEvent(ConnectEvent.NoWallet) },
                        fillMaxWidth = true,
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.connect_consent,
                    stringResource(R.string.terms_link),
                    stringResource(R.string.privacy_link),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegalLinks(
                onOpenTerms = { onEvent(ConnectEvent.OpenTerms) },
                onOpenPrivacy = { onEvent(ConnectEvent.OpenPrivacy) },
            )
            Text(
                text = stringResource(R.string.test_money_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A status node with a progress ring. The text is the only live node on the screen. */
@Composable
private fun WaitingStatus() {
    val waiting = stringResource(R.string.connect_waiting)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = waiting
        },
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(text = waiting, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FailurePanel(onEvent: (ConnectEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, LatiFiShapes.card)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.connect_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        Text(
            text = stringResource(R.string.connect_error_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        PrimaryButton(text = stringResource(R.string.connect_retry), onClick = { onEvent(ConnectEvent.Connect) })
        TertiaryButton(
            text = stringResource(R.string.connect_nowallet),
            onClick = { onEvent(ConnectEvent.NoWallet) },
            fillMaxWidth = true,
        )
    }
}
