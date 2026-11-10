package com.latifi.wallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import com.latifi.wallet.R
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.MinTouchTarget

/** The site the attribution row opens. The rates provider asks to be credited with this link. */
const val RATES_PROVIDER_URL = "https://www.exchangerate-api.com"

/**
 * "Rates By Exchange Rate API": a Caption link row, underlined in the link color, at least 48dp
 * high, announced as a button whose action reads "Rates By Exchange Rate API, opens website".
 * Every screen that shows a local estimate and Profile > About use it. [onOpen] defaults to a
 * Custom Tab on the provider site, and a test passes its own to see the tap.
 */
@Composable
fun AttributionRow(modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val open = onOpen ?: { CustomTabLauncher.open(context, RATES_PROVIDER_URL) }
    Box(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .widthIn(min = MinTouchTarget)
            .latifiFocusRing()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.rates_attribution_a11y),
                onClick = open,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.rates_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = LatiFiTheme.colors.link,
            textDecoration = TextDecoration.Underline,
        )
    }
}
