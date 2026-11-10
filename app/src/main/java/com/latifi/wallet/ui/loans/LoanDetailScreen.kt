package com.latifi.wallet.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.rates.LocalValue
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
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.StaleRateCaption
import com.latifi.wallet.ui.components.StatusChip
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger

private val NoteIconSize = 24.dp

/**
 * S08 Loan details (borrower), stateless. Top to bottom: the status chip, the amount with its local
 * estimate, the detail rows, the installments list and the attribution row. The one primary action
 * sits in the pinned bar and depends on the status: Pay installment for an active loan, a disabled
 * button with the reason for an overdue one. An open request shows the text "Waiting for a lender"
 * and a repaid loan shows a success note. An open request has no due dates yet, so it shows no
 * installments list.
 */
@Composable
fun LoanDetailScreen(
    state: LoanDetailUiState,
    onReload: () -> Unit,
    onBack: () -> Unit,
    onPay: (BigInteger) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = (state.content as? Loadable.Success)?.data
    ScreenScaffold(
        title = stringResource(R.string.loan_details_title),
        modifier = modifier,
        onBack = onBack,
        bottomBar = detail?.let { actionBar(it, onPay) },
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

/** The pinned primary action, or null when the status has none. */
private fun actionBar(detail: LoanDetail, onPay: (BigInteger) -> Unit): (@Composable () -> Unit)? =
    when (detail.action) {
        LoanAction.Pay -> {
            { PrimaryButton(text = stringResource(R.string.pay_cta), onClick = { onPay(detail.loan.loanId) }) }
        }
        LoanAction.Blocked -> {
            {
                PrimaryButton(
                    text = stringResource(R.string.pay_cta),
                    onClick = {},
                    enabled = false,
                    disabledReason = stringResource(R.string.pay_blocked),
                )
            }
        }
        LoanAction.WaitingForLender, LoanAction.Repaid -> null
    }

@Composable
private fun Body(detail: LoanDetail, state: LoanDetailUiState, modifier: Modifier) {
    val loan = detail.loan
    val locale = currentLocale()
    val today = rememberToday()
    val amount = TokenAmount(loan.principal)
    val local = LocalValueProvider.valueFrom(state.rates, amount, state.currency)
    val localOf = { value: TokenAmount -> LocalValueProvider.valueFrom(state.rates, value, state.currency) }

    val rows = buildList {
        add(DetailRowData(stringResource(R.string.detail_rate), stringResource(R.string.rate_per_year, PercentFormat.fromBasisPoints(loan.interestRateBps, locale))))
        add(DetailRowData(stringResource(R.string.detail_term), pluralStringResource(R.plurals.term_days, loan.termDays, loan.termDays)))
        add(DetailRowData(stringResource(R.string.detail_installments), pluralStringResource(R.plurals.installments_count, loan.installments, loan.installments)))
        val total = TokenAmount(detail.totalToRepay)
        add(DetailRowData(stringResource(R.string.detail_total), total.format(locale), estimateText(localOf(total))))
        val each = TokenAmount(detail.installmentAmount)
        add(DetailRowData(stringResource(R.string.detail_installment_amount), each.format(locale), estimateText(localOf(each))))
        loan.fundedAt?.let { add(DetailRowData(stringResource(R.string.detail_funded_on), DateFormat.short(it, locale, today))) }
        loan.nextDueDate?.let { add(DetailRowData(stringResource(R.string.detail_next_due), DateFormat.short(it, locale, today))) }
        if (loan.lender != null) {
            val who = state.lenderName ?: state.lenderShortAddress ?: loan.lender
            add(DetailRowData(stringResource(R.string.detail_lender), who))
        }
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
            Text(text = amount.format(locale), style = LatiFiType.Amount.Display)
            LocalValueLine(local)
        }
        when (detail.action) {
            LoanAction.WaitingForLender -> Text(text = stringResource(R.string.loan_waiting_lender), style = MaterialTheme.typography.bodyLarge)
            LoanAction.Repaid -> SuccessNote(stringResource(R.string.loan_repaid_note))
            LoanAction.Pay, LoanAction.Blocked -> Unit
        }
        DetailRows(rows)
        if (loan.status != LoanStatus.OPEN) {
            Text(
                text = stringResource(R.string.installments_list_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            InstallmentList(entries = detail.schedule, localValueOf = localOf)
        }
        AttributionRow()
    }
}

@Composable
private fun estimateText(value: LocalValue): String? = when (value) {
    is LocalValue.Value -> stringResource(R.string.local_estimate, value.currency.format(value.amount, currentLocale()))
    LocalValue.Unavailable -> stringResource(R.string.local_unavailable)
    LocalValue.Loading -> null
}

/** A success note: icon, text and the success container, never color alone. */
@Composable
private fun SuccessNote(text: String) {
    val colors = LatiFiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.successContainer, LatiFiShapes.chip)
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(NoteIconSize),
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = colors.success)
    }
}
