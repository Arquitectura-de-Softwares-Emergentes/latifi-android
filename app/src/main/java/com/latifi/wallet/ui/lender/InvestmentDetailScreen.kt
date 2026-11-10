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
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.DetailRowData
import com.latifi.wallet.ui.components.DetailRows
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.InstallmentList
import com.latifi.wallet.ui.components.LocalValueLine
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.StatusChip
import com.latifi.wallet.ui.components.localEstimateText
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

/**
 * S16 Investment details (lender), stateless. Top to bottom: the lent amount with its local
 * estimate, the status chip, the rows (rate, term, installments, expected return, next installment,
 * borrower), the installments list with Received, Due and Upcoming, and the attribution row. There
 * is no primary action: the lender only follows the loan. The chip is the status the API reports and
 * Overdue appears only for DEFAULTED, never worked out from a due date.
 */
@Composable
fun InvestmentDetailScreen(
    state: InvestmentDetailUiState,
    onReload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = stringResource(R.string.investment_details_title),
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Column(modifier = Modifier.padding(padding).padding(Spacing.lg)) {
                SkeletonList(count = 1, description = stringResource(R.string.loading_generic))
            }
            is Loadable.Error -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                ErrorPanel(kind = content.kind, onReload = onReload)
            }
            is Loadable.Success -> Body(content.data, state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Body(detail: InvestmentDetail, state: InvestmentDetailUiState, modifier: Modifier) {
    val loan = detail.loan
    val locale = currentLocale()
    val today = rememberToday()
    val lent = TokenAmount(loan.principal)
    val localOf = { value: TokenAmount -> LocalValueProvider.valueFrom(state.rates, value, state.currency) }

    val rows = buildList {
        add(DetailRowData(stringResource(R.string.detail_rate), stringResource(R.string.rate_per_year, PercentFormat.fromBasisPoints(loan.interestRateBps, locale))))
        add(DetailRowData(stringResource(R.string.detail_term), pluralStringResource(R.plurals.term_days, loan.termDays, loan.termDays)))
        add(DetailRowData(stringResource(R.string.detail_installments), pluralStringResource(R.plurals.installments_count, loan.installments, loan.installments)))
        val expected = TokenAmount(detail.expectedReturn)
        add(DetailRowData(stringResource(R.string.investment_expected), expected.format(locale), localEstimateText(localOf(expected))))
        loan.nextDueDate?.let { add(DetailRowData(stringResource(R.string.detail_next_due), DateFormat.short(it, locale, today))) }
        val short = state.borrowerShortAddress ?: loan.borrower
        add(DetailRowData(stringResource(R.string.detail_borrower), state.borrowerName ?: short, short.takeIf { state.borrowerName != null }))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        StatusChip(loan.uiStatus)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = stringResource(R.string.investment_lent), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = lent.format(locale), style = LatiFiType.Amount.Display)
            LocalValueLine(localOf(lent))
        }
        DetailRows(rows)
        Text(
            text = stringResource(R.string.installments_list_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        InstallmentList(entries = detail.schedule, localValueOf = localOf, received = true)
        AttributionRow()
    }
}
