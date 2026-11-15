package com.latifi.wallet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.ui.reputation.ReputationFormat
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType

/** The ring color of a level, from the reputation tokens. */
@Composable
fun reputationColor(level: ReputationLevel): Color {
    val colors = LatiFiTheme.colors
    return when (level) {
        ReputationLevel.NEW -> colors.repNew
        ReputationLevel.BUILDING -> colors.repBuilding
        ReputationLevel.TRUSTED -> colors.repTrusted
        ReputationLevel.EXCELLENT -> colors.repExcellent
    }
}

/**
 * A ring that sweeps to the score when it first shows and follows later changes, with the number
 * in the middle. The chip or text beside it already says the score and level, so the ring is
 * hidden from screen readers and only repeats it. With reduced motion it appears already full.
 */
@Composable
fun ReputationRing(
    score: Int,
    level: ReputationLevel,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    showScore: Boolean = true,
    onHero: Boolean = false,
) {
    val motion = rememberMotion()
    val target = ReputationFormat.barFraction(score)
    val sweep = remember { Animatable(if (motion.enabled) 0f else target) }
    LaunchedEffect(target) { sweep.animateTo(target, motion.hero()) }
    val brand = LatiFiTheme.colors
    val color = if (onHero) brand.heroAccent else reputationColor(level)
    val track = if (onHero) brand.onHeroVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
    Box(modifier = modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(this.size.width - width, this.size.height - width)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
            if (sweep.value > 0f) {
                drawArc(color, 135f, 270f * sweep.value, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        if (!showScore) PulseMark(size = size * 0.42f, withBackground = false, pulseColor = color)
        if (showScore) {
            Text(text = score.toString(), style = LatiFiType.Amount.Title, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
