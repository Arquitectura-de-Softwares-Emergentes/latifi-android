package com.latifi.wallet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * A slim rounded bar that fills to [fraction] with the hero ease, or at once under reduced motion.
 * The text next to it is the source of truth, so the bar is hidden from screen readers.
 */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
) {
    val motion = rememberMotion()
    val target = fraction.coerceIn(0f, 1f)
    val shown = remember { Animatable(if (motion.enabled) 0f else target) }
    LaunchedEffect(target) { shown.animateTo(target, motion.hero()) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(track)
            .clearAndSetSemantics {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.value)
                .clip(CircleShape)
                .background(color),
        )
    }
}
