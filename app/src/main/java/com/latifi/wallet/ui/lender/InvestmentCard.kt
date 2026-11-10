package com.latifi.wallet.ui.lender

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.UiLoanStatus
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.components.LocalValueLine
import com.latifi.wallet.ui.components.StatusChip
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.components.localEstimateText
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger
import java.time.Instant

/** What an investment card shows, with the money already as a [TokenAmount]. */
data class InvestmentCardModel(
    val loanId: BigInteger,
    /** What the lender put in: the principal. */
    val lent: TokenAmount,
    /** What the lender gets back in total: the total due. */
    val expected: TokenAmount,
    /** Exactly the status the API reports, never worked out from dates. */
    val status: UiLoanStatus,
    val nextDue: Instant?,
)

fun Loan.toInvestmentModel(): InvestmentCardModel = InvestmentCardModel(
    loanId = loanId,
    lent = TokenAmount(principal),
    expected = TokenAmount(expectedReturn(this)),
    status = uiStatus,
    nextDue = nextDueDate,
)

/** The stored total of a funded loan, or the contract formula when the API still reports zero. */
internal fun expectedReturn(loan: Loan): BigInteger =
    if (loan.totalDue.signum() > 0) loan.totalDue else LoanMath.totalDue(loan.principal, loan.interestRateBps, loan.termDays)

/**
 * One investment on S15: the "Lent" amount, the status chip, the "Expected return" and the next
 * installment date, each money value with its local estimate. The card is one merged target with one
 * spoken sentence, for example "Lent: 250.00 tUSD, about S/ 937.50, Expected return: 275.00 tUSD, about
 * S/ 1,031.25, Status: Pending, Next installment: March 3". The pairs wrap and nothing is cut with a
 * line cap, so the card reflows at 200 percent text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvestmentCard(
    model: InvestmentCardModel,
    lentLocal: LocalValue,
    expectedLocal: LocalValue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val today = rememberToday()
    val lentLabel = stringResource(R.string.investment_lent)
    val expectedLabel = stringResource(R.string.investment_expected)
    val lentText = model.lent.format(locale)
    val expectedText = model.expected.format(locale)
    val lentLocalText = localEstimateText(lentLocal)
    val expectedLocalText = localEstimateText(expectedLocal)
    val nextDueText = model.nextDue?.let { stringResource(R.string.loan_next_due, DateFormat.short(it, locale, today)) }
    val statusText = stringResource(R.string.group_status) + ": " + stringResource(model.status.labelRes())
    val spoken = listOfNotNull(
        "$lentLabel: $lentText",
        lentLocalText,
        "$expectedLabel: $expectedText",
        expectedLocalText,
        statusText,
        nextDueText,
    ).joinToString(", ")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .latifiFocusRing(LatiFiShapes.card)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.investment_details_title),
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
                Text(text = lentLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = lentText, style = LatiFiType.Amount.Headline)
                StatusChip(model.status)
            }
            LocalValueLine(lentLocal, showStale = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = expectedLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = expectedText, style = LatiFiType.Amount.BodyL)
            }
            LocalValueLine(expectedLocal, showStale = false)
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
