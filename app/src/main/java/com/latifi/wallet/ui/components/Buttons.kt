package com.latifi.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

/** Control border of the secondary button, a stroke width and not a spacing token. */
private val ControlBorderWidth = 1.5.dp

/**
 * The one main action of a screen. Callers pass a label that names the object it acts on,
 * never a bare "Save" or "Continue" (the copy guards enforce this on the string resources).
 *
 * When [enabled] is false, [disabledReason] shows in plain text under the button so the
 * person knows what is missing. While [loading] the button shows a spinner and ignores taps.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    loading: Boolean = false,
    fillMaxWidth: Boolean = true,
) {
    ButtonWithReason(enabled, disabledReason, fillMaxWidth, modifier) { buttonModifier ->
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = buttonModifier,
            shape = LatiFiShapes.button,
        ) { ButtonLabel(text, loading) }
    }
}

/** An alternative action, outlined with the 1.5dp control border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    loading: Boolean = false,
    fillMaxWidth: Boolean = true,
) {
    ButtonWithReason(enabled, disabledReason, fillMaxWidth, modifier) { buttonModifier ->
        OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = buttonModifier,
            shape = LatiFiShapes.button,
            border = BorderStroke(ControlBorderWidth, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) { ButtonLabel(text, loading) }
    }
}

/** A low emphasis action drawn as text in the link color. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    loading: Boolean = false,
    fillMaxWidth: Boolean = false,
) {
    ButtonWithReason(enabled, disabledReason, fillMaxWidth, modifier) { buttonModifier ->
        TextButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = buttonModifier,
            shape = LatiFiShapes.button,
            colors = ButtonDefaults.textButtonColors(contentColor = LatiFiTheme.colors.link),
        ) { ButtonLabel(text, loading) }
    }
}

@Composable
private fun ButtonWithReason(
    enabled: Boolean,
    disabledReason: String?,
    fillMaxWidth: Boolean,
    modifier: Modifier,
    button: @Composable (Modifier) -> Unit,
) {
    val buttonModifier = Modifier
        .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = MinTouchTarget)
        .latifiFocusRing()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        button(buttonModifier)
        if (!enabled && disabledReason != null) {
            Text(
                text = disabledReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The label wraps onto more lines instead of truncating, so a long word never hides. */
@Composable
private fun RowScope.ButtonLabel(text: String, loading: Boolean) {
    if (loading) {
        val loadingText = stringResource(R.string.loading_generic)
        CircularProgressIndicator(
            modifier = Modifier
                .size(20.dp)
                .semantics { contentDescription = loadingText },
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
    )
}
