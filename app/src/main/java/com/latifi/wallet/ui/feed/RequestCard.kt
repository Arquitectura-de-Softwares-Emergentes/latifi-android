package com.latifi.wallet.ui.feed

import com.latifi.wallet.ui.components.LatiFiCard
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.components.LocalValueLine
import com.latifi.wallet.ui.components.ReputationChip
import com.latifi.wallet.ui.components.SecondaryButton
import com.latifi.wallet.ui.components.labelRes
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

private val ChevronSize = 24.dp

/**
 * One open request on S12. The card has two targets at most. The summary (amount, local estimate,
 * "Requested by", reputation chip, rate, term and installments, a trailing chevron) is one merged
 * target that opens the details and reads as one sentence, for example "250.00 tUSD, ≈ S/ 937.50,
 * Requested by Lucia Fernandez, Borrower reputation: Trusted, 620 of 1000, 10% a year, 30 days, 3
 * installments". Below it sits a separate full width outlined Fund button, spoken as "Fund request
 * of 250.00 tUSD". On the lender's own request the button is disabled and says why in plain text.
 * The pairs sit in a wrapping row and nothing is cut with a line cap, so the card reflows at 200
 * percent text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestCard(
    model: RequestCardModel,
    localValue: LocalValue,
    onOpen: () -> Unit,
    onFund: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val amountText = model.amount.format(locale)
    val nameText = model.name?.let { stringResource(R.string.requested_by, it) } ?: stringResource(R.string.name_not_shared)
    val rateText = stringResource(R.string.rate_per_year, PercentFormat.fromBasisPoints(model.rateBps, locale))
    val termText = pluralStringResource(R.plurals.term_days, model.termDays, model.termDays)
    val installmentsText = pluralStringResource(R.plurals.installments_count, model.installments, model.installments)
    val localText = (localValue as? LocalValue.Value)?.let {
        stringResource(R.string.local_estimate, it.currency.format(it.amount, locale))
    } ?: (localValue as? LocalValue.Unavailable)?.let { stringResource(R.string.local_unavailable) }
    val reputationText = stringResource(R.string.rep_chip_a11y, stringResource(model.level.labelRes()), model.score)
    val spoken = listOfNotNull(amountText, localText, nameText, reputationText, rateText, termText, installmentsText)
        .joinToString(", ")
    val openHint = stringResource(R.string.open_details_a11y)

    val source = remember { MutableInteractionSource() }
    LatiFiCard(modifier = modifier, interactionSource = source) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .latifiFocusRing(LatiFiShapes.card)
                    .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onOpen)
                    // The hint is the state text, so it reads after the sentence without a doubled "double tap".
                    .semantics(mergeDescendants = true) {
                        contentDescription = spoken
                        stateDescription = openHint
                    }
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(text = amountText, style = LatiFiType.Amount.Headline)
                    LocalValueLine(localValue, showStale = false)
                    Text(
                        text = nameText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (model.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    ReputationChip(level = model.level, score = model.score)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(text = rateText, style = MaterialTheme.typography.bodyMedium)
                        Text(text = termText, style = MaterialTheme.typography.bodyMedium)
                        Text(text = installmentsText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // The chevron only hints that the summary opens something, so it says nothing itself.
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ChevronSize),
                )
            }
            SecondaryButton(
                text = stringResource(R.string.fund_card_cta),
                onClick = onFund,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                enabled = !model.isOwn,
                disabledReason = if (model.isOwn) stringResource(R.string.fund_own_request) else null,
                spokenLabel = stringResource(R.string.fund_card_a11y, amountText),
            )
        }
    }
}
