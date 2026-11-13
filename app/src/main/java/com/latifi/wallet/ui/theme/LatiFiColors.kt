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
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroVariant: Color,
    val heroAccent: Color,
    val heroChip: Color,
    val limeContainer: Color,
    val onLimeContainer: Color,
    val limeTint: Color,
    val onLimeTint: Color,
    val repNew: Color,
    val repBuilding: Color,
    val repTrusted: Color,
    val repExcellent: Color,
) {
    /** The navy hero gradient, diagonal. */
    val heroGradient: List<Color> get() = listOf(heroStart, heroEnd)

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
            heroStart = Color(ColorTokens.Light.heroStart),
            heroEnd = Color(ColorTokens.Light.heroEnd),
            onHero = Color(ColorTokens.Light.onHero),
            onHeroVariant = Color(ColorTokens.Light.onHeroVariant),
            heroAccent = Color(ColorTokens.Light.heroAccent),
            heroChip = Color(ColorTokens.Light.heroChip),
            limeContainer = Color(ColorTokens.Light.limeContainer),
            onLimeContainer = Color(ColorTokens.Light.onLimeContainer),
            limeTint = Color(ColorTokens.Light.limeTint),
            onLimeTint = Color(ColorTokens.Light.onLimeTint),
            repNew = Color(ColorTokens.Light.repNew),
            repBuilding = Color(ColorTokens.Light.repBuilding),
            repTrusted = Color(ColorTokens.Light.repTrusted),
            repExcellent = Color(ColorTokens.Light.repExcellent),
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
            heroStart = Color(ColorTokens.Dark.heroStart),
            heroEnd = Color(ColorTokens.Dark.heroEnd),
            onHero = Color(ColorTokens.Dark.onHero),
            onHeroVariant = Color(ColorTokens.Dark.onHeroVariant),
            heroAccent = Color(ColorTokens.Dark.heroAccent),
            heroChip = Color(ColorTokens.Dark.heroChip),
            limeContainer = Color(ColorTokens.Dark.limeContainer),
            onLimeContainer = Color(ColorTokens.Dark.onLimeContainer),
            limeTint = Color(ColorTokens.Dark.limeTint),
            onLimeTint = Color(ColorTokens.Dark.onLimeTint),
            repNew = Color(ColorTokens.Dark.repNew),
            repBuilding = Color(ColorTokens.Dark.repBuilding),
            repTrusted = Color(ColorTokens.Dark.repTrusted),
            repExcellent = Color(ColorTokens.Dark.repExcellent),
        )
    }
}

val LocalLatiFiColors = staticCompositionLocalOf { LatiFiColors.Light }
