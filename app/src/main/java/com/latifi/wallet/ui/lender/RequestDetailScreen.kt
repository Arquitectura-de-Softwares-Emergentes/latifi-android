package com.latifi.wallet.ui.lender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.latifi.wallet.R
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.DetailRowData
import com.latifi.wallet.ui.components.DetailRows
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.LocalValueLine
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ReputationChip
import com.latifi.wallet.ui.components.ReputationEventRow
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.StatusChip
import com.latifi.wallet.ui.components.localEstimateText
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

/**
 * S13 Request details (lender), stateless, in the reading order of the UI-SPEC: the title, the
 * amount with its local estimate, the borrower's reputation chip, the rows (rate, term,
 * installments, installment amount, total, what the lender would earn, the borrower), the recent
 * payment history, the line that the terms are fixed once you fund, the attribution row and the
 * one primary action in the pinned bar.
 *
 * The primary is Fund request for an open request, disabled with its visible reason on the
 * lender's own request, and absent for any other status: the status chip says what the request
 * is, and the screen never offers an action the contract would reject.
 */
@Composable
fun RequestDetailScreen(
    state: RequestDetailUiState,
    onReload: () -> Unit,
    onReloadHistory: () -> Unit,
    onBack: () -> Unit,
    onFund: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = stringResource(R.string.detail_request_title),
        modifier = modifier,
        onBack = onBack,
        bottomBar = fundBar(state.fundAction, onFund),
    ) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Column(modifier = Modifier.padding(padding).padding(Spacing.lg)) {
                SkeletonList(count = 1, description = stringResource(R.string.loading_generic))
            }
            is Loadable.Error -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                ErrorPanel(kind = content.kind, onReload = onReload)
            }
            is Loadable.Success -> Body(content.data, state, onReloadHistory, Modifier.padding(padding))
        }
    }
}

/** The pinned primary action, or null when the request has none. */
private fun fundBar(action: FundAction, onFund: () -> Unit): (@Composable () -> Unit)? = when (action) {
    FundAction.Available -> {
        { PrimaryButton(text = stringResource(R.string.fund_cta), onClick = onFund) }
    }
    FundAction.OwnRequest -> {
        {
            PrimaryButton(
                text = stringResource(R.string.fund_cta),
                onClick = {},
                enabled = false,
                disabledReason = stringResource(R.string.fund_own_request),
            )
        }
    }
    FundAction.None -> null
}

@Composable
private fun Body(detail: RequestDetail, state: RequestDetailUiState, onReloadHistory: () -> Unit, modifier: Modifier) {
    val loan = detail.loan
    val locale = currentLocale()
    val amount = TokenAmount(loan.principal)
    val localOf = { value: TokenAmount -> LocalValueProvider.valueFrom(state.rates, value, state.currency) }

    val rows = buildList {
        add(DetailRowData(stringResource(R.string.detail_rate), stringResource(R.string.rate_per_year, PercentFormat.fromBasisPoints(loan.interestRateBps, locale))))
        add(DetailRowData(stringResource(R.string.detail_term), pluralStringResource(R.plurals.term_days, loan.termDays, loan.termDays)))
        add(DetailRowData(stringResource(R.string.detail_installments), pluralStringResource(R.plurals.installments_count, loan.installments, loan.installments)))
        val each = TokenAmount(detail.installmentAmount)
        add(DetailRowData(stringResource(R.string.detail_installment_amount), each.format(locale), localEstimateText(localOf(each))))
        val total = TokenAmount(detail.totalToRepay)
        add(DetailRowData(stringResource(R.string.detail_total), total.format(locale), localEstimateText(localOf(total))))
        val earn = TokenAmount(detail.earnings)
        add(DetailRowData(stringResource(R.string.detail_earn), earn.format(locale), localEstimateText(localOf(earn))))
        // The borrower row shows the public name with the short address under it, or the short address alone.
        val short = state.borrowerShortAddress ?: loan.borrower
        val borrowerValue = state.borrowerName ?: short
        add(DetailRowData(stringResource(R.string.detail_borrower), borrowerValue, short.takeIf { state.borrowerName != null }))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = amount.format(locale), style = LatiFiType.Amount.Display)
            LocalValueLine(localOf(amount))
        }
        ReputationChip(level = loan.borrowerReputation.level, score = loan.borrowerReputation.score)
        // An open request needs no chip. Any other status shows why there is no Fund request.
        if (state.fundAction == FundAction.None) StatusChip(loan.uiStatus)
        DetailRows(rows)
        Text(
            text = stringResource(R.string.detail_history_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        History(state.history, onReloadHistory)
        Text(text = stringResource(R.string.detail_terms_fixed), style = MaterialTheme.typography.bodyLarge)
        AttributionRow()
    }
}

@Composable
private fun History(history: RecentHistory, onReload: () -> Unit) {
    when (history) {
        RecentHistory.Loading -> SkeletonList(count = 1, description = stringResource(R.string.loading_generic))
        is RecentHistory.Error -> ErrorPanel(kind = history.kind, onReload = onReload)
        RecentHistory.NewBorrower -> Text(text = stringResource(R.string.detail_history_new), style = MaterialTheme.typography.bodyLarge)
        is RecentHistory.Items -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            history.events.forEach { event -> ReputationEventRow(event) }
        }
    }
}
