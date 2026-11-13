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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latifi.wallet.ui.theme.LatiFiTheme

/** The motif of an empty state. All are drawn in brand colors and say nothing to a screen reader. */
enum class Illustration { Pulse, Loans, Coins, Reputation, Filter }

/**
 * A small brand illustration for an empty state: a soft disc, a navy card or coin motif and a lime
 * accent. It rises in once with a settle spring (instant under reduced motion).
 */
@Composable
fun EmptyIllustration(kind: Illustration, modifier: Modifier = Modifier, size: Dp = 104.dp) {
    val motion = rememberMotion()
    val colors = LatiFiTheme.colors
    val enter = remember { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (motion.enabled) enter.animateTo(1f, motion.settle()) }
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = 0.85f + 0.15f * enter.value
                scaleY = 0.85f + 0.15f * enter.value
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .clearAndSetSemantics {},
    ) {
        val s = this.size.minDimension
        drawCircle(colors.limeTint, radius = s * 0.5f)
        drawCircle(Color(0xFFE8EFF9), radius = s * 0.36f, center = Offset(s * 0.5f, s * 0.54f))
        when (kind) {
            Illustration.Pulse -> pulseMotif(s)
            Illustration.Loans -> loansMotif(s)
            Illustration.Coins -> coinsMotif(s)
            Illustration.Reputation -> reputationMotif(s)
            Illustration.Filter -> filterMotif(s)
        }
    }
}

private fun DrawScope.pulseMotif(s: Float) {
    val navy = Color(0xFF0B1F3A)
    drawRoundRect(navy, Offset(s * 0.26f, s * 0.26f), Size(s * 0.48f, s * 0.48f), CornerRadius(s * 0.11f))
    inset(s * 0.26f) { drawPulse(Color(0xFFC6FF3D)) }
}

private fun DrawScope.loansMotif(s: Float) {
    val navy = Color(0xFF0B1F3A)
    val mid = Color(0xFF14407A)
    drawRoundRect(mid, Offset(s * 0.22f, s * 0.30f), Size(s * 0.5f, s * 0.34f), CornerRadius(s * 0.06f))
    drawRoundRect(navy, Offset(s * 0.30f, s * 0.38f), Size(s * 0.5f, s * 0.34f), CornerRadius(s * 0.06f))
    drawRoundRect(Color(0xFFC6FF3D), Offset(s * 0.36f, s * 0.46f), Size(s * 0.16f, s * 0.05f), CornerRadius(s * 0.025f))
    drawRoundRect(Color(0xFF8FB8FF), Offset(s * 0.36f, s * 0.57f), Size(s * 0.3f, s * 0.04f), CornerRadius(s * 0.02f))
}

private fun DrawScope.coinsMotif(s: Float) {
    val navy = Color(0xFF0B1F3A)
    val lime = Color(0xFFC6FF3D)
    drawCircle(navy, s * 0.15f, Offset(s * 0.38f, s * 0.58f))
    drawCircle(lime, s * 0.15f, Offset(s * 0.58f, s * 0.5f))
    drawCircle(Color(0xFF14407A), s * 0.15f, Offset(s * 0.5f, s * 0.36f))
    drawCircle(navy, s * 0.06f, Offset(s * 0.58f, s * 0.5f))
}

private fun DrawScope.reputationMotif(s: Float) {
    val navy = Color(0xFF0B1F3A)
    val stroke = Stroke(s * 0.075f, cap = StrokeCap.Round)
    val topLeft = Offset(s * 0.28f, s * 0.32f)
    val arc = Size(s * 0.44f, s * 0.44f)
    drawArc(Color(0xFFB8C4D6), 135f, 270f, false, topLeft, arc, style = stroke)
    drawArc(navy, 135f, 120f, false, topLeft, arc, style = stroke)
    drawCircle(Color(0xFFC6FF3D), s * 0.045f, Offset(s * 0.5f, s * 0.54f))
}

private fun DrawScope.filterMotif(s: Float) {
    val navy = Color(0xFF0B1F3A)
    val stroke = Stroke(s * 0.07f, cap = StrokeCap.Round)
    drawCircle(navy, s * 0.15f, Offset(s * 0.47f, s * 0.46f), style = stroke)
    drawLine(navy, Offset(s * 0.58f, s * 0.57f), Offset(s * 0.7f, s * 0.69f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFC6FF3D), s * 0.04f, Offset(s * 0.47f, s * 0.46f))
}
