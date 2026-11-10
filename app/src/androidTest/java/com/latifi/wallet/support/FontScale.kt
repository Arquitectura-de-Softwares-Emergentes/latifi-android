package com.latifi.wallet.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Runs [content] at a fixed font scale. The research prefers this to changing the system font
 * size, because it is deterministic and needs no device setting. Screen density stays as is.
 */
@Composable
fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = base.density, fontScale = fontScale),
        content = content,
    )
}
