package com.latifi.wallet.ui.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.UiLoanStatus
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.components.LocalValueLine
import com.latifi.wallet.ui.components.StatusChip
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger
import java.time.Instant

/** What a loan card shows, with the money already as a [TokenAmount]. */
data class LoanCardModel(
    val loanId: BigInteger,
    val amount: TokenAmount,
    val status: UiLoanStatus,
    val rateBps: Int,
    val termDays: Int,
    val installments: Int,
    val installmentsPaid: Int,
    val nextDue: Instant?,
)

fun Loan.toCardModel(): LoanCardModel = LoanCardModel(
    loanId = loanId,
    amount = TokenAmount(principal),
    status = uiStatus,
    rateBps = interestRateBps,
    termDays = termDays,
    installments = installments,
    installmentsPaid = installmentsPaid,
    nextDue = nextDueDate,
)

/**
 * The summary of one loan on S07: amount, local estimate, status chip, rate and term, the
 * installments progress as text with a bar that only repeats it, and the next due date. The card
 * is one merged target with one spoken sentence, for example "250.00 tUSD, ≈ S/ 937.50, Status:
 * Pending, 10% a year, 30 days, 1 of 3 installments paid, Next installment: March 3". The pairs sit
 * in a wrapping row and nothing is cut with a line cap, so the card reflows at 200 percent text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoanCard(
    model: LoanCardModel,
    localValue: LocalValue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val today = rememberToday()
    val amountText = model.amount.format(locale)
    val rateText = stringResource(R.string.rate_per_year, PercentFormat.fromBasisPoints(model.rateBps, locale))
    val termText = pluralStringResource(R.plurals.term_days, model.termDays, model.termDays)
    val progressText = stringResource(R.string.loan_progress, model.installmentsPaid, model.installments)
    val nextDueText = model.nextDue?.let { stringResource(R.string.loan_next_due, DateFormat.short(it, locale, today)) }
    val localText = (localValue as? LocalValue.Value)?.let {
        stringResource(R.string.local_estimate, it.currency.format(it.amount, locale))
    } ?: (localValue as? LocalValue.Unavailable)?.let { stringResource(R.string.local_unavailable) }
    val statusText = stringResource(R.string.group_status) + ": " + stringResource(model.status.labelRes())
    val spoken = listOfNotNull(amountText, localText, statusText, rateText, termText, progressText, nextDueText)
        .joinToString(", ")
    val fraction = if (model.installments > 0) model.installmentsPaid.toFloat() / model.installments else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .latifiFocusRing(LatiFiShapes.card)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.loan_details_title),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = LatiFiShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LatiFiTheme.colors.outlineDecor),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = amountText, style = LatiFiType.Amount.Headline)
                StatusChip(model.status)
            }
            LocalValueLine(localValue, showStale = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(text = rateText, style = MaterialTheme.typography.bodyMedium)
                Text(text = termText, style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = progressText, style = MaterialTheme.typography.bodyMedium)
            // The text above is the source of truth. The bar repeats it for sighted people.
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
            if (nextDueText != null) {
                Text(
                    text = nextDueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun UiLoanStatus.labelRes(): Int = when (this) {
    UiLoanStatus.Open -> R.string.status_open
    UiLoanStatus.Pending -> R.string.status_pending
    UiLoanStatus.Paid -> R.string.status_paid
    UiLoanStatus.Overdue -> R.string.status_overdue
}
