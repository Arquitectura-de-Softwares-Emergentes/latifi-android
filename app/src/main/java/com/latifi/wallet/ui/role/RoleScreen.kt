package com.latifi.wallet.ui.role

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

/**
 * S04. Two full width cards with radio semantics. The selected card shows a check icon and a
 * thicker border besides its color, and the screen reader says "selected". The primary action
 * stays off, with its reason in text, until a card is chosen.
 */
@Composable
fun RoleScreen(
    state: RoleUiState,
    onEvent: (RoleEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = stringResource(R.string.role_title),
        modifier = modifier,
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.role_cta),
                onClick = { onEvent(RoleEvent.Confirm) },
                enabled = state.selected != null,
                disabledReason = stringResource(R.string.role_cta_disabled),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                RoleCard(
                    title = R.string.role_borrow,
                    subtitle = R.string.role_borrow_sub,
                    selected = state.selected == AppRole.Borrower,
                    onSelect = { onEvent(RoleEvent.Select(AppRole.Borrower)) },
                )
                RoleCard(
                    title = R.string.role_lend,
                    subtitle = R.string.role_lend_sub,
                    selected = state.selected == AppRole.Lender,
                    onSelect = { onEvent(RoleEvent.Select(AppRole.Lender)) },
                )
            }
            Text(
                text = stringResource(R.string.role_change_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoleCard(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .latifiFocusRing(LatiFiShapes.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = LatiFiShapes.card,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.sm).size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
