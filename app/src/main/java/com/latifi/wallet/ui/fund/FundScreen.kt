package com.latifi.wallet.ui.fund

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.latifi.wallet.R
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.components.AmountBlock
import com.latifi.wallet.ui.components.DetailRowData
import com.latifi.wallet.ui.components.DetailRows
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.components.localEstimateText
import com.latifi.wallet.ui.theme.Spacing

/**
 * S14. The summary repeats what the lender saw (amount, rate, term, installments, the total the
 * borrower repays and what the lender would earn), says the terms are fixed when they sign, and
 * has one primary. A request that is no longer open shows its own copy and "Back to requests".
 */
@Composable
fun FundScreen(
    state: FundUiState,
    onConfirm: () -> Unit,
    onBackToRequests: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = (state.content as? Loadable.Success)?.data
    ScreenScaffold(
        title = stringResource(R.string.fund_title),
        modifier = modifier,
        onBack = onBack,
        bottomBar = data?.let {
            {
                if (state.notOpen) {
                    PrimaryButton(text = stringResource(R.string.back_to_requests), onClick = onBackToRequests)
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.fund_confirm),
                        onClick = onConfirm,
                        enabled = !state.ownRequest,
                        disabledReason = if (state.ownRequest) stringResource(R.string.fund_own_request) else null,
                        loading = state.waitingForIndexer,
                    )
                }
            }
        },
    ) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Column(Modifier.padding(padding).padding(Spacing.lg)) {
                SkeletonList(count = 1, description = stringResource(R.string.loading_generic))
            }
            is Loadable.Error -> Column(Modifier.padding(padding).fillMaxSize()) {
                ErrorPanel(kind = content.kind, onReload = onReload)
            }
            is Loadable.Success -> FundBody(content.data, state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun FundBody(data: FundData, state: FundUiState, modifier: Modifier) {
    val loan = data.detail.loan
    val locale = currentLocale()
    val localOf = { value: TokenAmount -> LocalValueProvider.valueFrom(state.rates, value, state.currency) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (state.notOpen) {
            Text(
                text = stringResource(R.string.tx_fail_notopen),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            return@Column
        }
        val principal = TokenAmount(loan.principal)
        AmountBlock(label = stringResource(R.string.group_amount), amount = principal, local = localOf(principal))
        val rows = buildList {
            add(DetailRowData(stringResource(R.string.detail_rate), stringResource(R.string.rate_per_year, PercentFormat.fromBasisPoints(loan.interestRateBps, locale))))
            add(DetailRowData(stringResource(R.string.detail_term), pluralStringResource(R.plurals.term_days, loan.termDays, loan.termDays)))
            add(DetailRowData(stringResource(R.string.detail_installments), pluralStringResource(R.plurals.installments_count, loan.installments, loan.installments)))
            val total = TokenAmount(data.detail.totalToRepay)
            add(DetailRowData(stringResource(R.string.detail_total), total.format(locale), localEstimateText(localOf(total))))
            val earn = TokenAmount(data.detail.earnings)
            add(DetailRowData(stringResource(R.string.detail_earn), earn.format(locale), localEstimateText(localOf(earn))))
        }
        DetailRows(rows)
        Text(text = stringResource(R.string.fund_terms_fixed), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.network_fee_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.needsWallet) {
            Text(text = stringResource(R.string.tx_fail_session), color = MaterialTheme.colorScheme.error)
        }
        if (state.waitingForIndexer) {
            Text(
                text = stringResource(R.string.loading_loans),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
