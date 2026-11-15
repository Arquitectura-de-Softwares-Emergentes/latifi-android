package com.latifi.wallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.latifi.wallet.R
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

/**
 * A labelled amount with its local estimate under it: "Your balance", `1,000.00 tUSD` in the
 * tabular amount style (`tnum` figures, Display by default, pass Headline for a secondary block) and `≈ S/ 3,750.00`
 * in the line below. The block is one merged node that reads as one phrase, for example "Your
 * balance, 1,000.00 tUSD, about S/ 3,750.00", so a screen reader does not stop three times. The
 * amount wraps at large text sizes and is never cut.
 */
@Composable
fun AmountBlock(
    label: String,
    amount: TokenAmount,
    local: LocalValue,
    modifier: Modifier = Modifier,
    style: TextStyle = LatiFiType.Amount.Display,
    onHero: Boolean = false,
    countUp: Boolean = false,
) {
    val locale = currentLocale()
    val amountText = amount.format(locale)
    val localSpoken = when (local) {
        is LocalValue.Value -> stringResource(R.string.local_about_a11y, local.currency.format(local.amount, locale))
        LocalValue.Unavailable -> stringResource(R.string.local_unavailable)
        LocalValue.Loading -> null
    }
    val spoken = listOfNotNull(label, amountText, localSpoken).joinToString(", ")
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (onHero) LatiFiTheme.colors.onHeroVariant else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val shown = if (countUp) rememberCountUp(amount.baseUnits).value else amount.baseUnits
        Text(
            text = if (countUp) TokenAmount(shown).format(locale) else amountText,
            style = style,
            color = if (onHero) LatiFiTheme.colors.onHero else Color.Unspecified,
        )
        LocalValueLine(
            local,
            color = if (onHero) LatiFiTheme.colors.onHeroVariant else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
