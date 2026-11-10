package com.latifi.wallet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.components.AmountBlock
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonCard
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.Spacing

/**
 * S06 Home, stateless. This first slice holds the balance block: the amount as a Display value
 * with its local estimate, a skeleton while it loads and an error panel with Reload when the
 * read fails.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.nav_home), modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (val balance = state.balance) {
                Loadable.Loading -> SkeletonCard()
                is Loadable.Error -> ErrorPanel(kind = balance.kind, onReload = { onEvent(HomeEvent.ReloadBalance) })
                is Loadable.Success -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LatiFiShapes.card,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, LatiFiTheme.colors.outlineDecor),
                ) {
                    AmountBlock(
                        label = stringResource(R.string.home_balance),
                        amount = balance.data,
                        local = LocalValueProvider.valueFrom(state.rates, balance.data, state.currency),
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
    }
}
