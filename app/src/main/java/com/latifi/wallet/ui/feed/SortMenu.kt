package com.latifi.wallet.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.feed.FeedSort
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.MinTouchTarget

private val IconSize = 24.dp
private val ControlBorderWidth = 1.5.dp

/** The word of a sort order, as the button and the menu show it. */
internal fun FeedSort.labelRes(): Int = when (this) {
    FeedSort.Reputation -> R.string.sort_reputation
    FeedSort.Rate -> R.string.sort_rate
    FeedSort.Term -> R.string.sort_term
    FeedSort.Recent -> R.string.sort_recent
}

/**
 * The Sort button of S12: 48dp high, it reads "Sort: Reputation" and opens a menu with four radio
 * items. The button text is a polite live region, so a change is announced as "Sort: Rate" without
 * moving focus. The chosen item shows a check icon as well as its radio state. Choosing an item
 * closes the menu.
 */
@Composable
fun SortMenu(
    sort: FeedSort,
    onSelect: (FeedSort) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .latifiFocusRing()
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = LatiFiShapes.button,
            border = BorderStroke(ControlBorderWidth, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.sort_label, stringResource(sort.labelRes())),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FeedSort.entries.forEach { option ->
                val isSelected = option == sort
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes()), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize),
                            )
                        } else {
                            Spacer(Modifier.size(IconSize))
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = MinTouchTarget)
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                        },
                )
            }
        }
    }
}
