package com.latifi.wallet.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.latifi.wallet.ui.components.rememberEntranceTracker
import com.latifi.wallet.ui.components.motionItem
import com.latifi.wallet.ui.components.staggerIn
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
import com.latifi.wallet.R
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.ChipOption
import com.latifi.wallet.ui.components.EmptyPanel
import com.latifi.wallet.ui.components.Illustration
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.LiveCounter
import com.latifi.wallet.ui.components.LoadMoreButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.StaleRateCaption
import com.latifi.wallet.ui.components.StatusFilterChips
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger

/** The list starts to load the next page when the last visible row is this close to the end. */
private const val AUTO_LOAD_DISTANCE = 3

/**
 * S07 Loans (borrower), stateless. The header holds the status chips, the polite counter, a Refresh
 * button for people who cannot pull, the attribution row and, when the rates are stale, one
 * caption for the whole list. Below it the body is one of: three skeleton cards, an error panel
 * with Reload (no data), the empty state with a Request a loan button, the filtered-empty state
 * with a Show all button, or the cards with a Load more footer. A failed refresh with data on
 * screen is a snackbar and the list stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    state: LoansUiState,
    onEvent: (LoansEvent) -> Unit,
    onOpenLoan: (BigInteger) -> Unit,
    onRequestLoan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val tracker = rememberEntranceTracker()
    
    val failedText = stringResource(R.string.refresh_failed)
    val reloadText = stringResource(R.string.retry_reload)

    LaunchedEffect(state.refreshFailed) {
        if (state.refreshFailed) {
            val result = snackbar.showSnackbar(message = failedText, actionLabel = reloadText)
            onEvent(LoansEvent.RefreshFailedShown)
            if (result == SnackbarResult.ActionPerformed) onEvent(LoansEvent.Refresh)
        }
    }
    LaunchedEffect(state.resetToken) { listState.scrollToItem(0) }

    val items = state.content as? LoansContent.Items
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - AUTO_LOAD_DISTANCE
        }
    }
    LaunchedEffect(nearEnd, items?.hasMore, items?.cards?.size) {
        if (nearEnd && items?.hasMore == true && !state.isLoadingMore) onEvent(LoansEvent.LoadMore)
    }

    ScreenScaffold(
        title = stringResource(R.string.loans_title),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(LoansEvent.Refresh) },
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
                    LoansContent.Loading -> item(key = "loading") { SkeletonList() }
                    is LoansContent.Error -> item(key = "error") {
                        ErrorPanel(kind = content.kind, onReload = { onEvent(LoansEvent.Reload) })
                    }
                    LoansContent.Empty -> item(key = "empty") {
                        EmptyPanel(
                            illustration = Illustration.Loans,
                            title = stringResource(R.string.loans_empty_title),
                            body = stringResource(R.string.loans_empty_body),
                            actionLabel = stringResource(R.string.request_title),
                            onAction = onRequestLoan,
                        )
                    }
                    LoansContent.FilteredEmpty -> item(key = "filtered-empty") {
                        EmptyPanel(
                            illustration = Illustration.Filter,
                            title = stringResource(R.string.loans_filtered_empty_title),
                            body = stringResource(R.string.loans_filtered_empty_body),
                            actionLabel = stringResource(R.string.loans_show_all),
                            onAction = { onEvent(LoansEvent.ShowAll) },
                        )
                    }
                    is LoansContent.Items -> {
                        itemsIndexed(content.cards, key = { _, card -> card.loanId.toString() }) { index, card ->
                            LoanCard(
                                modifier = motionItem().staggerIn(index, card.loanId, tracker),
                                model = card,
                                localValue = LocalValueProvider.valueFrom(state.rates, card.amount, state.currency),
                                onClick = { onOpenLoan(card.loanId) },
                            )
                        }
                        if (content.hasMore) {
                            item(key = "more") {
                                LoadMoreButton(onClick = { onEvent(LoansEvent.LoadMore) }, loading = state.isLoadingMore)
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
private fun Header(state: LoansUiState, onEvent: (LoansEvent) -> Unit) {
    val options = LoanFilter.entries.map { ChipOption(it, stringResource(it.labelRes())) }
    val counterText = state.counter?.let { count ->
        pluralStringResource(R.plurals.counter_loans, count.toPluralQuantity(), count.toPluralQuantity())
    }
    val sample = LocalValueProvider.valueFrom(state.rates, TokenAmount.ofTokens(1), state.currency)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusFilterChips(
            label = stringResource(R.string.group_status),
            options = options,
            selected = state.filter,
            onSelect = { onEvent(LoansEvent.SelectFilter(it)) },
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LiveCounter(text = counterText, loadingText = stringResource(R.string.loading_loans))
            TertiaryButton(
                text = stringResource(R.string.refresh),
                onClick = { onEvent(LoansEvent.Refresh) },
                loading = state.isRefreshing,
            )
        }
        AttributionRow()
        if (sample is LocalValue.Value && sample.stale) StaleRateCaption(sample.asOf)
    }
}

private fun LoanFilter.labelRes(): Int = when (this) {
    LoanFilter.All -> R.string.status_all
    LoanFilter.Open -> R.string.status_open
    LoanFilter.Pending -> R.string.status_pending
    LoanFilter.Paid -> R.string.status_paid
    LoanFilter.Overdue -> R.string.status_overdue
}

/** Plural quantities are Int. A count above Int.MAX_VALUE cannot happen for a person's loans, but the cast stays safe. */
private fun Long.toPluralQuantity(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
