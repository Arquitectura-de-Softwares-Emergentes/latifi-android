package com.latifi.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme

/** The meaning a card or an icon chip tints itself with. Color always comes with an icon and words. */
enum class Tone { Neutral, Info, Success, Warning, Danger, Accent }

@Immutable
data class ToneColors(val container: Color, val content: Color, val border: Color)

/** The container, content and border of a [Tone] in the current theme. */
@Composable
fun toneColors(tone: Tone): ToneColors {
    val colors = LatiFiTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        Tone.Neutral -> ToneColors(scheme.surface, scheme.onSurface, colors.outlineDecor)
        Tone.Info -> ToneColors(colors.infoContainer, colors.link, colors.link.copy(alpha = 0.18f))
        Tone.Success -> ToneColors(colors.successContainer, colors.success, colors.success.copy(alpha = 0.22f))
        Tone.Warning -> ToneColors(colors.warningContainer, colors.warning, colors.warning.copy(alpha = 0.25f))
        Tone.Danger -> ToneColors(scheme.errorContainer, scheme.error, scheme.error.copy(alpha = 0.25f))
        Tone.Accent -> ToneColors(colors.limeTint, colors.onLimeTint, colors.success.copy(alpha = 0.28f))
    }
}

/**
 * The card of the LatiFi design system: a [LatiFiShapes.card] surface that is flat with a hairline
 * border, tinted by [tone]. Text on a tinted card keeps the normal on-surface color, so the tint
 * never lowers contrast. Pass the same [interactionSource] you give `clickable` to get the press
 * scale of the system.
 */
@Composable
fun LatiFiCard(
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val look = toneColors(tone)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .let { if (interactionSource != null) it.pressScale(interactionSource) else it },
        shape = LatiFiShapes.card,
        color = look.container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, look.border),
    ) { content() }
}

/** A rounded square holding one decorative icon in the colors of a [Tone]. The words stay beside it. */
@Composable
fun IconChip(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    size: Dp = 40.dp,
    onHero: Boolean = false,
) {
    val look = toneColors(tone)
    val colors = LatiFiTheme.colors
    val container = if (onHero) colors.heroChip else look.container
    val tint = if (onHero) colors.heroAccent else look.content
    Box(
        modifier = modifier
            .size(size)
            .clip(LatiFiShapes.tile)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
