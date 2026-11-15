package com.latifi.wallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(ColorTokens.Light.primary),
    onPrimary = Color(ColorTokens.Light.onPrimary),
    primaryContainer = Color(ColorTokens.Light.secondaryContainer),
    onPrimaryContainer = Color(ColorTokens.Light.primary),
    secondary = Color(ColorTokens.Light.link),
    onSecondary = Color(ColorTokens.Light.onPrimary),
    secondaryContainer = Color(ColorTokens.Light.secondaryContainer),
    onSecondaryContainer = Color(ColorTokens.Light.primary),
    background = Color(ColorTokens.Light.background),
    onBackground = Color(ColorTokens.Light.onSurface),
    surface = Color(ColorTokens.Light.surface),
    onSurface = Color(ColorTokens.Light.onSurface),
    surfaceVariant = Color(ColorTokens.Light.surface),
    onSurfaceVariant = Color(ColorTokens.Light.onSurfaceVariant),
    surfaceTint = Color(ColorTokens.Light.primary),
    surfaceContainerLowest = Color(ColorTokens.Light.background),
    surfaceContainerLow = Color(ColorTokens.Light.surface),
    surfaceContainer = Color(ColorTokens.Light.surface),
    surfaceContainerHigh = Color(ColorTokens.Light.surface),
    surfaceContainerHighest = Color(ColorTokens.Light.surface),
    outline = Color(ColorTokens.Light.outline),
    outlineVariant = Color(ColorTokens.Light.outlineDecor),
    error = Color(ColorTokens.Light.error),
    onError = Color(ColorTokens.Light.onPrimary),
    errorContainer = Color(ColorTokens.Light.errorContainer),
    onErrorContainer = Color(ColorTokens.Light.error),
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(ColorTokens.Dark.primary),
    onPrimary = Color(ColorTokens.Dark.onPrimary),
    primaryContainer = Color(ColorTokens.Dark.secondaryContainer),
    onPrimaryContainer = Color(ColorTokens.Dark.onSurface),
    secondary = Color(ColorTokens.Dark.link),
    onSecondary = Color(ColorTokens.Dark.onPrimary),
    secondaryContainer = Color(ColorTokens.Dark.secondaryContainer),
    onSecondaryContainer = Color(ColorTokens.Dark.onSurface),
    background = Color(ColorTokens.Dark.background),
    onBackground = Color(ColorTokens.Dark.onSurface),
    surface = Color(ColorTokens.Dark.surface),
    onSurface = Color(ColorTokens.Dark.onSurface),
    surfaceVariant = Color(ColorTokens.Dark.surface),
    onSurfaceVariant = Color(ColorTokens.Dark.onSurfaceVariant),
    surfaceTint = Color(ColorTokens.Dark.primary),
    surfaceContainerLowest = Color(ColorTokens.Dark.background),
    surfaceContainerLow = Color(ColorTokens.Dark.surface),
    surfaceContainer = Color(ColorTokens.Dark.surface),
    surfaceContainerHigh = Color(ColorTokens.Dark.surface),
    surfaceContainerHighest = Color(ColorTokens.Dark.surface),
    outline = Color(ColorTokens.Dark.outline),
    outlineVariant = Color(ColorTokens.Dark.outlineDecor),
    error = Color(ColorTokens.Dark.error),
    onError = Color(ColorTokens.Dark.onPrimary),
    errorContainer = Color(ColorTokens.Dark.errorContainer),
    onErrorContainer = Color(ColorTokens.Dark.error),
)

/**
 * The only theme of the app. Brand colors are fixed, so dynamic color is never used:
 * wallpaper colors would break the contrast table.
 */
@Composable
fun LatiFiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLatiFiColors provides if (darkTheme) LatiFiColors.Dark else LatiFiColors.Light,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = LatiFiTypography,
            shapes = LatiFiMaterialShapes,
            content = content,
        )
    }
}

/** Access to the extended semantic colors from any composable under LatiFiTheme. */
object LatiFiTheme {
    val colors: LatiFiColors
        @Composable get() = LocalLatiFiColors.current
}
