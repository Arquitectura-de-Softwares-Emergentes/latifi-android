package com.latifi.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
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
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.InstallmentEntry
import com.latifi.wallet.domain.model.InstallmentState
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

private val StateIconSize = 20.dp

/**
 * One row per installment: its number, due date, amount with the local estimate, and the state as an
 * icon plus a word (Paid, Due, Upcoming). With [received] the paid word is Received, which is how the
 * lender sees an installment that came in; the borrower keeps Paid. Each row is one merged node spoken through
 * `installment_row_a11y`, for example "Installment 2 of 3, 83.33 tUSD, due March 3, Due". The pieces
 * wrap, so nothing is cut at 200 percent text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallmentList(
    entries: List<InstallmentEntry>,
    localValueOf: (TokenAmount) -> LocalValue,
    modifier: Modifier = Modifier,
    received: Boolean = false,
) {
    val locale = currentLocale()
    val today = rememberToday()
    Column(modifier = modifier.fillMaxWidth()) {
        entries.forEachIndexed { position, entry ->
            val amount = TokenAmount(entry.amount)
            val amountText = amount.format(locale)
            val dateText = entry.dueAt?.let { DateFormat.short(it, locale, today) }.orEmpty()
            val look = entry.state.look(received)
            val stateText = stringResource(look.label)
            val spoken = stringResource(R.string.installment_row_a11y, entry.index, entries.size, amountText, dateText, stateText)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = entry.index.toString(), style = MaterialTheme.typography.titleMedium)
                Text(text = dateText, style = MaterialTheme.typography.bodyLarge)
                Column {
                    Text(text = amountText, style = LatiFiType.Amount.BodyL)
                    LocalValueLine(localValueOf(amount), showStale = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(look.icon),
                        contentDescription = null,
                        tint = look.tint(),
                        modifier = Modifier.size(StateIconSize),
                    )
                    Text(text = stateText, style = MaterialTheme.typography.labelLarge, color = look.tint())
                }
            }
            if (position < entries.lastIndex) HorizontalDivider(color = LatiFiTheme.colors.outlineDecor)
        }
    }
}

private enum class Tint { Success, Link, Muted }

private data class StateLook(@DrawableRes val icon: Int, @StringRes val label: Int, val tone: Tint)

@Composable
private fun StateLook.tint(): Color = when (tone) {
    Tint.Success -> LatiFiTheme.colors.success
    Tint.Link -> LatiFiTheme.colors.link
    Tint.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun InstallmentState.look(received: Boolean): StateLook = when (this) {
    InstallmentState.PAID -> StateLook(
        R.drawable.ic_check_circle,
        if (received) R.string.installment_state_received else R.string.installment_state_paid,
        Tint.Success,
    )
    InstallmentState.DUE -> StateLook(R.drawable.ic_payments, R.string.installment_state_due, Tint.Link)
    InstallmentState.UPCOMING -> StateLook(R.drawable.ic_schedule, R.string.installment_state_upcoming, Tint.Muted)
}
