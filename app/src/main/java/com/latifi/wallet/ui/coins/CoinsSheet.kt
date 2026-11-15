package com.latifi.wallet.ui.coins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.latifi.wallet.R
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.Spacing

/**
 * S18 content, stateless so a test can show it alone. The body holds the per-wallet cap, so the
 * person reads the limit before asking. The wallet signs the request on the status sheet that follows.
 */
@Composable
fun CoinsSheetContent(
    needsWallet: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.coins_title)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(text = stringResource(R.string.coins_body), style = MaterialTheme.typography.bodyLarge)
        if (needsWallet) {
            Text(
                text = stringResource(R.string.tx_fail_session),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        PrimaryButton(text = stringResource(R.string.coins_confirm), onClick = onConfirm)
    }
}

/** The bottom sheet of [Overlays.COINS][com.latifi.wallet.ui.nav.Overlays.COINS]. Back or a swipe closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinsSheet() {
    val viewModel: CoinsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = viewModel::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LatiFiShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CoinsSheetContent(needsWallet = state.needsWallet, onConfirm = viewModel::confirm)
    }
}
