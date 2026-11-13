package com.latifi.wallet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latifi.wallet.ui.theme.LatiFiTheme

/** The isotype pulse in a 100 by 100 box: the same points as the web favicon. */
private fun pulsePath(): Path = Path().apply {
    moveTo(20.4f, 53.2f)
    lineTo(40.8f, 53.2f)
    lineTo(48.4f, 31.8f)
    lineTo(57.9f, 74.1f)
    lineTo(64.9f, 53.2f)
    lineTo(79.7f, 53.2f)
}

/** Draws the pulse line and its two dots, scaled from the 100 unit design box to this scope. */
fun DrawScope.drawPulse(color: Color, progress: Float = 1f) {
    val unit = size.minDimension / 100f
    val path = pulsePath()
    val drawn = if (progress >= 1f) {
        path
    } else {
        Path().also { out ->
            val measure = PathMeasure().apply { setPath(path, false) }
            measure.getSegment(0f, measure.length * progress, out, true)
        }
    }
    withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
        drawPath(drawn, color, style = Stroke(width = 8.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (progress > 0f) drawCircle(color, radius = 10f, center = Offset(20.4f, 53.2f))
        if (progress >= 0.98f) drawCircle(color, radius = 10f, center = Offset(79.7f, 53.2f))
    }
}

/**
 * The LatiFi mark: a navy rounded square with a lime pulse. With [animateDraw] the pulse draws on
 * once when it first shows (instant under reduced motion). Decorative, so it says nothing to a
 * screen reader.
 */
@Composable
fun PulseMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animateDraw: Boolean = false,
    withBackground: Boolean = true,
    pulseColor: Color = LatiFiTheme.colors.heroAccent,
    backgroundColor: Color = LatiFiTheme.colors.heroStart,
) {
    val motion = rememberMotion()
    val progress = remember { Animatable(if (animateDraw && motion.enabled) 0f else 1f) }
    LaunchedEffect(animateDraw) {
        if (animateDraw && motion.enabled) progress.animateTo(1f, motion.hero())
    }
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        if (withBackground) {
            drawRoundRect(backgroundColor, size = Size(this.size.width, this.size.height), cornerRadius = CornerRadius(this.size.minDimension * 0.22f))
        }
        drawPulse(pulseColor, progress.value)
    }
}
