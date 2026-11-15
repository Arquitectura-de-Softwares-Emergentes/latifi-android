package com.latifi.wallet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latifi.wallet.ui.theme.LatiFiMotion
import com.latifi.wallet.ui.theme.LatiFiTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val ConfettiCount = 14

/**
 * The confirmation moment of a finished transaction: a green disc that springs in, a check that
 * draws itself, and a small burst of brand colored dots, with one confirming haptic. It plays once
 * when it enters the screen. Under reduced motion it is the finished badge with no burst and no
 * movement. Decorative: the status title beside it carries the meaning.
 */
@Composable
fun SuccessBadge(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val motion = rememberMotion()
    val haptics = rememberHaptics()
    val colors = LatiFiTheme.colors
    val disc = remember { Animatable(if (motion.enabled) 0f else 1f) }
    val check = remember { Animatable(if (motion.enabled) 0f else 1f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptics.confirm()
        if (motion.enabled) {
            coroutineScope {
                launch { disc.animateTo(1f, motion.settle()) }
                launch {
                    check.animateTo(1f, motion.medium())
                }
                launch { burst.animateTo(1f, motion.hero()) }
            }
        }
    }
    val palette = listOf(colors.heroAccent, colors.success, colors.link, colors.limeContainer)
    Box(modifier = modifier.size(size * 1.6f).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size * 1.6f)) {
            val p = burst.value
            if (p > 0f && p < 1f) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = size.toPx() * (0.55f + 0.45f * p)
                repeat(ConfettiCount) { i ->
                    val angle = (i * 360f / ConfettiCount) * (Math.PI.toFloat() / 180f)
                    val dot = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    drawCircle(
                        color = palette[i % palette.size].copy(alpha = 1f - p),
                        radius = (if (i % 2 == 0) 4.dp else 3.dp).toPx() * (1f - p * 0.4f),
                        center = dot,
                    )
                }
            }
        }
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = disc.value
                    scaleY = disc.value
                },
        ) {
            drawCircle(colors.success)
            val unit = this.size.minDimension
            val path = Path().apply {
                moveTo(unit * 0.28f, unit * 0.52f)
                lineTo(unit * 0.44f, unit * 0.67f)
                lineTo(unit * 0.73f, unit * 0.36f)
            }
            val out = Path()
            val measure = PathMeasure().apply { setPath(path, false) }
            measure.getSegment(0f, measure.length * check.value, out, true)
            drawPath(
                out,
                Color.White,
                style = Stroke(width = unit * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Kept for tests: the burst never outlives the hero duration. */
internal const val SuccessBurstMillis = LatiFiMotion.Duration.Hero
