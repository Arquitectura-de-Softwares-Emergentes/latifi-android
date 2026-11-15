package com.latifi.wallet.ui.connect

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.latifi.wallet.R
import com.latifi.wallet.ui.components.SecondaryButton
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.Spacing

/** The plain steps for a person with no wallet. The content is stateless so a test can show it alone. */
@Composable
fun WalletHelpContent(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.connect_help_title)
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
        Text(text = stringResource(R.string.connect_help_body), style = MaterialTheme.typography.bodyLarge)
        SecondaryButton(text = stringResource(R.string.connect_help_close), onClick = onClose)
    }
}

/** The bottom sheet around [WalletHelpContent]. Back, a swipe or the button all close it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletHelpSheet(onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LatiFiShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        WalletHelpContent(onClose = onClose)
    }
}
