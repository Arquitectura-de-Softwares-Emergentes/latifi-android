package com.latifi.wallet.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that Material 3 has no slot for. Read them with `LatiFiTheme.colors`.
 * Semantic colors always appear together with an icon and text, never alone.
 */
data class LatiFiColors(
    val link: Color,
    val info: Color,
    val infoContainer: Color,
    val focusRing: Color,
    val outlineDecor: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val neutralContainer: Color,
    val neutralText: Color,
    val neutralBorder: Color,
    /** Decorative fill only in light theme. */
    val coral: Color,
) {
    companion object {
        val Light = LatiFiColors(
            link = Color(ColorTokens.Light.link),
            info = Color(ColorTokens.Light.info),
            infoContainer = Color(ColorTokens.Light.infoContainer),
            focusRing = Color(ColorTokens.Light.focusRing),
            outlineDecor = Color(ColorTokens.Light.outlineDecor),
            success = Color(ColorTokens.Light.success),
            successContainer = Color(ColorTokens.Light.successContainer),
            warning = Color(ColorTokens.Light.warning),
            warningContainer = Color(ColorTokens.Light.warningContainer),
            neutralContainer = Color(ColorTokens.Light.neutralContainer),
            neutralText = Color(ColorTokens.Light.neutralText),
            neutralBorder = Color(ColorTokens.Light.neutralBorder),
            coral = Color(ColorTokens.Coral500),
        )

        val Dark = LatiFiColors(
            link = Color(ColorTokens.Dark.link),
            info = Color(ColorTokens.Dark.info),
            infoContainer = Color(ColorTokens.Dark.infoContainer),
            focusRing = Color(ColorTokens.Dark.focusRing),
            outlineDecor = Color(ColorTokens.Dark.outlineDecor),
            success = Color(ColorTokens.Dark.success),
            successContainer = Color(ColorTokens.Dark.successContainer),
            warning = Color(ColorTokens.Dark.warning),
            warningContainer = Color(ColorTokens.Dark.warningContainer),
            neutralContainer = Color(ColorTokens.Dark.neutralContainer),
            neutralText = Color(ColorTokens.Dark.neutralText),
            neutralBorder = Color(ColorTokens.Dark.neutralBorder),
            coral = Color(ColorTokens.Coral500),
        )
    }
}

val LocalLatiFiColors = staticCompositionLocalOf { LatiFiColors.Light }
