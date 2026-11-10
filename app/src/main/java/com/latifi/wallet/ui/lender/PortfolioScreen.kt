package com.latifi.wallet.ui.lender

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.latifi.wallet.R
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.ChipOption
import com.latifi.wallet.ui.components.EmptyPanel
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.LiveCounter
import com.latifi.wallet.ui.components.LoadMoreButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.StaleRateCaption
import com.latifi.wallet.ui.components.StatusFilterChips
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.nav.Routes
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger

/** The list starts to load the next page when the last visible row is this close to the end. */
private const val AUTO_LOAD_DISTANCE = 3

/**
 * The Portfolio tab body. [rootController] is the app NavHost, because the investment details
 * open above the tabs. [onBrowse] selects the Invest tab. The chip lives in the view model of this
 * back stack entry, so it is still there when the person comes back from a detail.
 */
@Composable
fun PortfolioRoute(rootController: NavHostController, onBrowse: () -> Unit) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    PortfolioScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenInvestment = { id -> rootController.navigate(Routes.investmentDetail(id)) },
        onBrowse = onBrowse,
    )
}

/**
 * S15 Portfolio (lender), stateless. The header holds the status chips (All, Pending, Paid,
 * Overdue), the polite counter, a Refresh button for people who cannot pull, the attribution row
 * and, when the rates are stale, one caption for the whole list. Below it the body is one of: three
 * skeleton cards, an error panel with Reload (no data), the empty state with a Browse requests
 * button, the filtered-empty state with a Show all button, or the cards with a Load more footer. A
 * failed refresh with data on screen is a snackbar and the list stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    state: PortfolioUiState,
    onEvent: (PortfolioEvent) -> Unit,
    onOpenInvestment: (BigInteger) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val failedText = stringResource(R.string.refresh_failed)
    val reloadText = stringResource(R.string.retry_reload)

    LaunchedEffect(state.refreshFailed) {
        if (state.refreshFailed) {
            val result = snackbar.showSnackbar(message = failedText, actionLabel = reloadText)
            onEvent(PortfolioEvent.RefreshFailedShown)
            if (result == SnackbarResult.ActionPerformed) onEvent(PortfolioEvent.Refresh)
        }
    }
    LaunchedEffect(state.resetToken) { listState.scrollToItem(0) }

    val items = state.content as? PortfolioContent.Items
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - AUTO_LOAD_DISTANCE
        }
    }
    LaunchedEffect(nearEnd, items?.hasMore, items?.cards?.size) {
        if (nearEnd && items?.hasMore == true && !state.isLoadingMore) onEvent(PortfolioEvent.LoadMore)
    }

    ScreenScaffold(
        title = stringResource(R.string.portfolio_title),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(PortfolioEvent.Refresh) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item(key = "header") { Header(state, onEvent) }
                when (val content = state.content) {
                    PortfolioContent.Loading -> item(key = "loading") { SkeletonList() }
                    is PortfolioContent.Error -> item(key = "error") {
                        ErrorPanel(kind = content.kind, onReload = { onEvent(PortfolioEvent.Reload) })
                    }
                    PortfolioContent.Empty -> item(key = "empty") {
                        EmptyPanel(
                            title = stringResource(R.string.portfolio_empty_title),
                            body = stringResource(R.string.portfolio_empty_body),
                            actionLabel = stringResource(R.string.home_browse),
                            onAction = onBrowse,
                            primaryAction = true,
                        )
                    }
                    PortfolioContent.FilteredEmpty -> item(key = "filtered-empty") {
                        EmptyPanel(
                            title = stringResource(R.string.loans_filtered_empty_title),
                            body = stringResource(R.string.loans_filtered_empty_body),
                            actionLabel = stringResource(R.string.loans_show_all),
                            onAction = { onEvent(PortfolioEvent.ShowAll) },
                        )
                    }
                    is PortfolioContent.Items -> {
                        items(content.cards, key = { it.loanId.toString() }) { card ->
                            InvestmentCard(
                                model = card,
                                lentLocal = LocalValueProvider.valueFrom(state.rates, card.lent, state.currency),
                                expectedLocal = LocalValueProvider.valueFrom(state.rates, card.expected, state.currency),
                                onClick = { onOpenInvestment(card.loanId) },
                            )
                        }
                        if (content.hasMore) {
                            item(key = "more") {
                                LoadMoreButton(onClick = { onEvent(PortfolioEvent.LoadMore) }, loading = state.isLoadingMore)
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
private fun Header(state: PortfolioUiState, onEvent: (PortfolioEvent) -> Unit) {
    val options = PortfolioFilter.entries.map { ChipOption(it, stringResource(it.labelRes())) }
    val counterText = state.counter?.let { count ->
        val quantity = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        pluralStringResource(R.plurals.counter_loans, quantity, quantity)
    }
    val sample = LocalValueProvider.valueFrom(state.rates, TokenAmount.ofTokens(1), state.currency)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusFilterChips(
            label = stringResource(R.string.group_status),
            options = options,
            selected = state.filter,
            onSelect = { onEvent(PortfolioEvent.SelectFilter(it)) },
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LiveCounter(text = counterText, loadingText = stringResource(R.string.loading_loans))
            TertiaryButton(
                text = stringResource(R.string.refresh),
                onClick = { onEvent(PortfolioEvent.Refresh) },
                loading = state.isRefreshing,
            )
        }
        AttributionRow()
        if (sample is LocalValue.Value && sample.stale) StaleRateCaption(sample.asOf)
    }
}

private fun PortfolioFilter.labelRes(): Int = when (this) {
    PortfolioFilter.All -> R.string.status_all
    PortfolioFilter.Pending -> R.string.status_pending
    PortfolioFilter.Paid -> R.string.status_paid
    PortfolioFilter.Overdue -> R.string.status_overdue
}
