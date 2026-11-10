package com.latifi.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

/** One choice of a [StatusFilterChips] group. [text] is the visible word. */
data class ChipOption<T>(val value: T, val text: String)

/** Test tag of the check icon on the selected chip, so a UI test can prove it is there. */
const val FILTER_CHECK_TAG = "filter_check"

private val CheckSize = 18.dp
private val ChipBorder = 1.5.dp

/**
 * A single-select group of chips under a heading [label] (for example "Status"). The selected chip
 * has a check icon, a 1.5dp border in the link color and a filled container, so selection never
 * depends on color alone. Each chip is at least 48dp high and is spoken as "Status, Pending,
 * selected" or "Status, Pending, not selected". The chips wrap onto more rows instead of scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> StatusFilterChips(
    label: String,
    options: List<ChipOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEach { option ->
                FilterChipItem(
                    group = label,
                    text = option.text,
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

/**
 * One chip of a single-select group, also used by the Invest feed for its three filter groups and
 * for the removable chips above the counter ([removable] adds a decorative close icon). A tap on
 * the selected chip is the caller's to read as "toggle off".
 */
@Composable
internal fun FilterChipItem(
    group: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    removable: Boolean = false,
) {
    val spoken = stringResource(
        if (selected) R.string.chip_selected_a11y else R.string.chip_not_selected_a11y,
        group,
        text,
    )
    val colors = LatiFiTheme.colors
    Surface(
        modifier = Modifier
            .heightIn(min = MinTouchTarget)
            .latifiFocusRing(LatiFiShapes.chip)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken },
        shape = LatiFiShapes.chip,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) ChipBorder else 1.dp,
            if (selected) colors.link else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = MinTouchTarget).padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.link,
                    modifier = Modifier.size(CheckSize).testTag(FILTER_CHECK_TAG),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
            if (removable) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    tint = colors.link,
                    modifier = Modifier.size(CheckSize),
                )
            }
        }
    }
}
