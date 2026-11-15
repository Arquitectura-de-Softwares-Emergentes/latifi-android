package com.latifi.wallet.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.feed.AmountRange
import com.latifi.wallet.domain.feed.FeedFilters
import com.latifi.wallet.domain.feed.Risk
import com.latifi.wallet.domain.feed.TermLimit
import com.latifi.wallet.ui.components.FilterChipItem
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

private val IconSize = 24.dp
private val ControlBorderWidth = 1.5.dp

/** The label of each chip, shared by the groups and by the removable chips above the counter. */
@Composable
internal fun riskLabel(chip: Risk): String = stringResource(
    when (chip) {
        Risk.Lowest -> R.string.risk_lowest
        Risk.Low -> R.string.risk_low
        Risk.Medium -> R.string.risk_medium
    },
)

@Composable
internal fun termLabel(chip: TermLimit): String = pluralStringResource(R.plurals.term_up_to, chip.days, chip.days)

@Composable
internal fun amountLabel(chip: AmountRange): String = stringResource(
    when (chip) {
        AmountRange.Under100 -> R.string.amount_under
        AmountRange.From100To500 -> R.string.amount_between
        AmountRange.Over500 -> R.string.amount_over
    },
)

/**
 * The Filters button of S12. It reads "Filters" or "Filters, 2 active" and offers the Expand or the
 * Collapse action for the panel it controls, so a screen reader knows the state and sighted people
 * see the arrow. It is 48dp high and can be disabled while the first page loads.
 */
@Composable
fun FiltersToggle(
    activeCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = if (activeCount > 0) {
        pluralStringResource(R.plurals.filters_active, activeCount, activeCount)
    } else {
        stringResource(R.string.filters_toggle)
    }
    OutlinedButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .latifiFocusRing()
            .semantics {
                if (expanded) {
                    collapse { onToggle(); true }
                } else {
                    expand { onToggle(); true }
                }
            },
        shape = LatiFiShapes.button,
        border = BorderStroke(ControlBorderWidth, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Icon(painter = painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(IconSize))
        Spacer(Modifier.size(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.size(4.dp))
        Icon(
            painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
        )
    }
}

/**
 * The three single-select groups of the expanded Filters panel: Risk (with its help line), Term and
 * Amount. Each heading is a Title and a heading for a screen reader. A chip shows a check icon and
 * a border and fill change when selected, is at least 48dp high, is spoken as "Risk, Low risk,
 * selected" and toggles off when tapped again. The chips wrap onto more rows, so nothing scrolls
 * sideways or is cut at 200 percent text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedFilterPanel(
    filters: FeedFilters,
    onRisk: (Risk) -> Unit,
    onTerm: (TermLimit) -> Unit,
    onAmount: (AmountRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val riskGroup = stringResource(R.string.group_risk)
    val termGroup = stringResource(R.string.group_term)
    val amountGroup = stringResource(R.string.group_amount)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        FilterGroupSection(title = riskGroup, help = stringResource(R.string.risk_help)) {
            Risk.entries.forEach { chip ->
                FilterChipItem(riskGroup, riskLabel(chip), filters.risk == chip, onClick = { onRisk(chip) })
            }
        }
        FilterGroupSection(title = termGroup) {
            TermLimit.entries.forEach { chip ->
                FilterChipItem(termGroup, termLabel(chip), filters.term == chip, onClick = { onTerm(chip) })
            }
        }
        FilterGroupSection(title = amountGroup) {
            AmountRange.entries.forEach { chip ->
                FilterChipItem(amountGroup, amountLabel(chip), filters.amount == chip, onClick = { onAmount(chip) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroupSection(title: String, help: String? = null, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (help != null) {
            Text(
                text = help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { chips() }
    }
}

/**
 * The removable chips above the counter, one per selected group, each with a check icon and a close
 * icon. One tap removes the filter. They read the same as the chips of the panel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFilterChips(
    filters: FeedFilters,
    onRemove: (FilterGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val riskGroup = stringResource(R.string.group_risk)
    val termGroup = stringResource(R.string.group_term)
    val amountGroup = stringResource(R.string.group_amount)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        filters.risk?.let {
            FilterChipItem(riskGroup, riskLabel(it), selected = true, onClick = { onRemove(FilterGroup.Risk) }, removable = true)
        }
        filters.term?.let {
            FilterChipItem(termGroup, termLabel(it), selected = true, onClick = { onRemove(FilterGroup.Term) }, removable = true)
        }
        filters.amount?.let {
            FilterChipItem(amountGroup, amountLabel(it), selected = true, onClick = { onRemove(FilterGroup.Amount) }, removable = true)
        }
    }
}
