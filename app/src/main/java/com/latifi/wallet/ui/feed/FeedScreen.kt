package com.latifi.wallet.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.latifi.wallet.R
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.LiveCounter
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger

/**
 * The Invest tab body. The view model lives in this back stack entry, so the feed keeps its place
 * when the person comes back from a detail. The request details (plan 05-10) and the funding flow
 * (plan 05-12) are not registered yet, so both callbacks do nothing here: a tap never navigates to
 * a route that does not exist.
 */
@Composable
fun FeedRoute() {
    val viewModel: FeedViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    FeedScreen(
        state = state,
        onOpen = { _: BigInteger -> },
        onFund = { _: BigInteger -> },
    )
}

/** S12 Invest feed, stateless. The tracer slice shows the counter, the attribution and the cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedUiState,
    onOpen: (BigInteger) -> Unit,
    onFund: (BigInteger) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.resetToken) { listState.scrollToItem(0) }

    ScreenScaffold(title = stringResource(R.string.feed_title), modifier = modifier) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {},
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item(key = "header") { Header(state) }
                when (val content = state.content) {
                    FeedContent.Loading -> item(key = "loading") {
                        SkeletonList(description = stringResource(R.string.loading_requests))
                    }
                    is FeedContent.Error -> item(key = "error") { ErrorPanel(kind = content.kind, onReload = {}) }
                    FeedContent.Empty, FeedContent.FilteredEmpty -> Unit
                    is FeedContent.Items -> items(content.cards, key = { it.loanId.toString() }) { card ->
                        RequestCard(
                            model = card,
                            localValue = LocalValueProvider.valueFrom(state.rates, card.amount, state.currency),
                            onOpen = { onOpen(card.loanId) },
                            onFund = { onFund(card.loanId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: FeedUiState) {
    val counterText = state.counter?.let { count ->
        val quantity = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        pluralStringResource(R.plurals.counter_requests, quantity, quantity)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LiveCounter(text = counterText, loadingText = stringResource(R.string.loading_requests))
        AttributionRow()
    }
}
