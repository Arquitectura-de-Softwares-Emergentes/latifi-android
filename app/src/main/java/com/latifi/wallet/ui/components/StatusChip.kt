package com.latifi.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.UiLoanStatus
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.Spacing

private val ChipIconSize = 16.dp

private data class ChipLook(
    val container: Color,
    val content: Color,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @StringRes val spoken: Int,
)

/**
 * The loan status as the UI-SPEC status table draws it: an icon, the word and a color pair, always
 * together, never the color alone. A screen reader hears the longer sentence of `status_*_a11y`.
 * The chip is never derived from dates: Overdue shows only when the API reports DEFAULTED.
 */
@Composable
fun StatusChip(status: UiLoanStatus, modifier: Modifier = Modifier) {
    val colors = LatiFiTheme.colors
    val look = when (status) {
        UiLoanStatus.Open -> ChipLook(colors.infoContainer, colors.link, R.drawable.ic_lock_open, R.string.status_open, R.string.status_open_a11y)
        UiLoanStatus.Pending -> ChipLook(colors.warningContainer, colors.warning, R.drawable.ic_hourglass_top, R.string.status_pending, R.string.status_pending_a11y)
        UiLoanStatus.Paid -> ChipLook(colors.successContainer, colors.success, R.drawable.ic_check_circle, R.string.status_paid, R.string.status_paid_a11y)
        UiLoanStatus.Overdue -> ChipLook(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            R.drawable.ic_error,
            R.string.status_overdue,
            R.string.status_overdue_a11y,
        )
    }
    val spoken = stringResource(look.spoken)
    Row(
        modifier = modifier
            .background(look.container, LatiFiShapes.chip)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(look.icon),
            contentDescription = null,
            tint = look.content,
            modifier = Modifier.size(ChipIconSize),
        )
        Text(text = stringResource(look.label), style = MaterialTheme.typography.labelMedium, color = look.content)
    }
}
