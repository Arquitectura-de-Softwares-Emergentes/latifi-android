package com.latifi.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.Spacing
import java.time.Instant

private val CaptionIconSize = 16.dp

/**
 * The local estimate under a tUSD amount, in Body M secondary text: `≈ S/ 937.50`. With no rate it
 * reads "Local value unavailable", and while rates load it draws nothing, because the tUSD amount
 * above it is already the whole truth. [showStale] adds the stale caption under the estimate. A
 * list passes false and shows one [StaleRateCaption] for the whole screen instead of one per card.
 */
@Composable
fun LocalValueLine(
    value: LocalValue,
    modifier: Modifier = Modifier,
    showStale: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    when (value) {
        LocalValue.Loading -> Unit
        LocalValue.Unavailable -> Text(
            text = stringResource(R.string.local_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = modifier,
        )
        is LocalValue.Value -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.local_estimate, value.currency.format(value.amount, currentLocale())),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            if (showStale && value.stale) StaleRateCaption(value.asOf)
        }
    }
}

/** "Rate from {time}. It may be out of date." in a warning container with an icon. */
@Composable
fun StaleRateCaption(asOf: Instant, modifier: Modifier = Modifier) {
    val colors = LatiFiTheme.colors
    val stamp = DateFormat.stamp(asOf, currentLocale(), rememberToday())
    Row(
        modifier = modifier
            .background(colors.warningContainer, LatiFiShapes.chip)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(CaptionIconSize),
        )
        Text(
            text = stringResource(R.string.local_stale, stamp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.warning,
        )
    }
}
