package com.latifi.wallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.Spacing

/**
 * The brand hero: a navy to navy 700 gradient surface with a large faint pulse mark in the corner.
 * Everything inside reads in white and soft blue, and lime marks the accents. Contrast of those
 * pairs is held by `ThemeContrastTest`.
 */
@Composable
fun BalanceHero(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LatiFiTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LatiFiShapes.hero)
            .background(Brush.linearGradient(colors.heroGradient)),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(160.dp)
                .rotate(-8f)
                .clearAndSetSemantics {},
        ) {
            drawPulse(colors.heroAccent.copy(alpha = 0.14f))
        }
        Box(modifier = Modifier.padding(Spacing.xl)) {
            CompositionLocalProvider(LocalContentColor provides colors.onHero) { content() }
        }
    }
}
