package com.latifi.wallet.ui.feed

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.latifi.wallet.R
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.EmptyPanel
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.LiveCounter
import com.latifi.wallet.ui.components.LoadMoreButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.StaleRateCaption
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.math.BigInteger

/** The list starts to load the next page when the last visible row is this close to the end. */
private const val AUTO_LOAD_DISTANCE = 3

/**
 * The Invest tab body. The view model lives in this back stack entry, so the filters and the sort
 * are still there when the person comes back from a detail. The request details (plan 05-10) and
 * the funding flow (plan 05-12) are not registered yet, so both callbacks do nothing here: a tap
 * never navigates to a route that does not exist.
 */
@Composable
fun FeedRoute() {
    val viewModel: FeedViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    FeedScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpen = { _: BigInteger -> },
        onFund = { _: BigInteger -> },
        effects = viewModel.effects,
    )
}

/**
 * S12 Invest feed, stateless. Top to bottom: the title, the Sort and Filters row, the removable
 * chips, the filter groups when expanded, the polite counter with Refresh and (only with an active
 * filter) Clear filters, the attribution row and the cards. The body is one of: three skeleton
 * cards with the header controls disabled, an error panel with Reload (no data), the no-requests
 * state with no action, the no-match state with the screen's only primary action, or the cards with
 * a Load more footer. A failed refresh with data on screen is a snackbar and the list stays.
 *
 * [effects] carries the one-time results of the view model: Clear filters puts focus back on the
 * counter, and removing a chip says "Filter removed" politely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedUiState,
    onEvent: (FeedEvent) -> Unit,
    onOpen: (BigInteger) -> Unit,
    onFund: (BigInteger) -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<FeedEffect> = emptyFlow(),
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val counterFocus = remember { FocusRequester() }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val failedText = stringResource(R.string.refresh_failed)
    val reloadText = stringResource(R.string.retry_reload)
    val removedText = stringResource(R.string.filter_removed_a11y)

    LaunchedEffect(state.refreshFailed) {
        if (state.refreshFailed) {
            val result = snackbar.showSnackbar(message = failedText, actionLabel = reloadText)
            onEvent(FeedEvent.RefreshFailedShown)
            if (result == SnackbarResult.ActionPerformed) onEvent(FeedEvent.Refresh)
        }
    }
    LaunchedEffect(state.resetToken) { listState.scrollToItem(0) }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                // The counter sits in the header, so it is attached unless the list was scrolled away.
                FeedEffect.FocusCounter -> runCatching { counterFocus.requestFocus() }
                FeedEffect.FilterRemoved -> {
                    snackbar.currentSnackbarData?.dismiss()
                    launch { snackbar.showSnackbar(message = removedText, duration = SnackbarDuration.Short) }
                }
            }
        }
    }

    val items = state.content as? FeedContent.Items
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - AUTO_LOAD_DISTANCE
        }
    }
    LaunchedEffect(nearEnd, items?.hasMore, items?.cards?.size) {
        if (nearEnd && items?.hasMore == true && !state.isLoadingMore) onEvent(FeedEvent.LoadMore)
    }

    ScreenScaffold(
        title = stringResource(R.string.feed_title),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(FeedEvent.Refresh) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item(key = "header") {
                    Header(
                        state = state,
                        expanded = filtersExpanded,
                        onToggleExpanded = { filtersExpanded = !filtersExpanded },
                        counterFocus = counterFocus,
                        onEvent = onEvent,
                    )
                }
                when (val content = state.content) {
                    FeedContent.Loading -> item(key = "loading") {
                        SkeletonList(description = stringResource(R.string.loading_requests))
                    }
                    is FeedContent.Error -> item(key = "error") {
                        ErrorPanel(kind = content.kind, onReload = { onEvent(FeedEvent.Reload) })
                    }
                    FeedContent.Empty -> item(key = "empty") {
                        EmptyPanel(
                            title = stringResource(R.string.feed_empty_title),
                            body = stringResource(R.string.feed_empty_body),
                        )
                    }
                    FeedContent.FilteredEmpty -> item(key = "filtered-empty") {
                        EmptyPanel(
                            title = stringResource(R.string.feed_filtered_empty_title),
                            body = stringResource(R.string.feed_filtered_empty_body),
                            actionLabel = stringResource(R.string.filter_clear),
                            onAction = { onEvent(FeedEvent.ClearFilters) },
                            primaryAction = true,
                        )
                    }
                    is FeedContent.Items -> {
                        items(content.cards, key = { it.loanId.toString() }) { card ->
                            RequestCard(
                                model = card,
                                localValue = LocalValueProvider.valueFrom(state.rates, card.amount, state.currency),
                                onOpen = { onOpen(card.loanId) },
                                onFund = { onFund(card.loanId) },
                            )
                        }
                        if (content.hasMore) {
                            item(key = "more") {
                                LoadMoreButton(onClick = { onEvent(FeedEvent.LoadMore) }, loading = state.isLoadingMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: FeedUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    counterFocus: FocusRequester,
    onEvent: (FeedEvent) -> Unit,
) {
    val counterText = state.counter?.let { count ->
        val quantity = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        pluralStringResource(R.plurals.counter_requests, quantity, quantity)
    }
    val sample = LocalValueProvider.valueFrom(state.rates, TokenAmount.ofTokens(1), state.currency)
    val controlsEnabled = state.content !is FeedContent.Loading
    // The no-match state carries its own primary Clear filters, so the text button would be a twin.
    val showClear = state.hasActiveFilters && state.content !is FeedContent.FilteredEmpty

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SortMenu(
                sort = state.filters.sort,
                onSelect = { onEvent(FeedEvent.SetSort(it)) },
                enabled = controlsEnabled,
            )
            FiltersToggle(
                activeCount = state.activeFilterCount,
                expanded = expanded,
                onToggle = onToggleExpanded,
                enabled = controlsEnabled,
            )
        }
        if (state.hasActiveFilters) {
            ActiveFilterChips(filters = state.filters, onRemove = { onEvent(FeedEvent.RemoveFilter(it)) })
        }
        if (expanded) {
            FeedFilterPanel(
                filters = state.filters,
                onRisk = { onEvent(FeedEvent.SelectRisk(it)) },
                onTerm = { onEvent(FeedEvent.SelectTerm(it)) },
                onAmount = { onEvent(FeedEvent.SelectAmount(it)) },
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LiveCounter(
                text = counterText,
                loadingText = stringResource(R.string.loading_requests),
                modifier = Modifier.focusRequester(counterFocus).focusable(),
            )
            TertiaryButton(
                text = stringResource(R.string.refresh),
                onClick = { onEvent(FeedEvent.Refresh) },
                loading = state.isRefreshing,
            )
            if (showClear) {
                TertiaryButton(text = stringResource(R.string.filter_clear), onClick = { onEvent(FeedEvent.ClearFilters) })
            }
        }
        AttributionRow()
        if (sample is LocalValue.Value && sample.stale) StaleRateCaption(sample.asOf)
    }
}
