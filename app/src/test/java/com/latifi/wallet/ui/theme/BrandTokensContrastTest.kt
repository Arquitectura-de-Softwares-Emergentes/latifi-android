package com.latifi.wallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Contrast of the design system tokens added after the UI-SPEC table: the navy hero, the lime
 * accent, the tinted cards, the selected tab pill and the reputation ring. Same WCAG 2.1 formula as
 * [ThemeContrastTest], over the real token constants, in light and dark.
 */
class BrandTokensContrastTest {
    private data class Check(val name: String, val fg: Int, val bg: Int, val min: Double)

    private val text = 4.5
    private val nonText = 3.0
    private val l = ColorTokens.Light
    private val d = ColorTokens.Dark

    private val checks = listOf(
        // Hero, light
        Check("light onHero on heroStart", l.onHero, l.heroStart, text),
        Check("light onHero on heroEnd", l.onHero, l.heroEnd, text),
        Check("light onHeroVariant on heroStart", l.onHeroVariant, l.heroStart, text),
        Check("light onHeroVariant on heroEnd", l.onHeroVariant, l.heroEnd, text),
        Check("light lime on heroStart", l.heroAccent, l.heroStart, text),
        Check("light lime on heroEnd", l.heroAccent, l.heroEnd, text),
        Check("light lime on heroChip (icon)", l.heroAccent, l.heroChip, nonText),
        Check("light onHeroVariant on heroChip", l.onHeroVariant, l.heroChip, text),
        // Hero, dark
        Check("dark onHero on heroStart", d.onHero, d.heroStart, text),
        Check("dark onHero on heroEnd", d.onHero, d.heroEnd, text),
        Check("dark onHeroVariant on heroStart", d.onHeroVariant, d.heroStart, text),
        Check("dark onHeroVariant on heroEnd", d.onHeroVariant, d.heroEnd, text),
        Check("dark lime on heroStart", d.heroAccent, d.heroStart, text),
        Check("dark lime on heroChip (icon)", d.heroAccent, d.heroChip, nonText),
        Check("dark onHeroVariant on heroChip", d.onHeroVariant, d.heroChip, text),
        // Selected tab pill and lime tint
        Check("light onLimeContainer on limeContainer (tab icon)", l.onLimeContainer, l.limeContainer, text),
        Check("dark onLimeContainer on limeContainer (tab icon)", d.onLimeContainer, d.limeContainer, text),
        Check("light onLimeTint on limeTint (accent card)", l.onLimeTint, l.limeTint, text),
        Check("dark onLimeTint on limeTint (accent card)", d.onLimeTint, d.limeTint, text),
        Check("light success on limeTint (accent card icon)", l.success, l.limeTint, text),
        Check("dark lime on limeTint", d.heroAccent, d.limeTint, text),
        Check("light primary on limeContainer", l.primary, l.limeContainer, text),
        // Tinted card text
        Check("light onSurface on infoContainer", l.onSurface, l.infoContainer, text),
        Check("light onSurface on successContainer", l.onSurface, l.successContainer, text),
        Check("light onSurface on warningContainer", l.onSurface, l.warningContainer, text),
        Check("light onSurface on errorContainer", l.onSurface, l.errorContainer, text),
        Check("dark onSurface on infoContainer", d.onSurface, d.infoContainer, text),
        Check("dark onSurface on successContainer", d.onSurface, d.successContainer, text),
        Check("dark onSurface on warningContainer", d.onSurface, d.warningContainer, text),
        Check("dark onSurface on errorContainer", d.onSurface, d.errorContainer, text),
        // Reputation ring and gauge on a card surface
        Check("light repNew on surface", l.repNew, l.surface, nonText),
        Check("light repBuilding on surface", l.repBuilding, l.surface, nonText),
        Check("light repTrusted on surface", l.repTrusted, l.surface, nonText),
        Check("light repExcellent on surface", l.repExcellent, l.surface, nonText),
        Check("dark repNew on surface", d.repNew, d.surface, nonText),
        Check("dark repBuilding on surface", d.repBuilding, d.surface, nonText),
        Check("dark repTrusted on surface", d.repTrusted, d.surface, nonText),
        Check("dark repExcellent on surface", d.repExcellent, d.surface, nonText),
    )

    @Test
    fun everyBrandPairMeetsItsThreshold() {
        val failures = checks.filter { ratio(it.fg, it.bg) < it.min }
            .map { "${it.name}: ${"%.2f".format(ratio(it.fg, it.bg))} needs ${it.min}" }
        assertTrue("Pairs below threshold:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun brandColorsMatchTheLandingPage() {
        assertEquals(0xFF0B1F3A.toInt(), l.heroStart)
        assertEquals(0xFF14407A.toInt(), l.heroEnd)
        assertEquals(0xFFC6FF3D.toInt(), ColorTokens.Lime400)
        assertEquals(0xFFD8FF7A.toInt(), ColorTokens.Lime300)
        assertEquals(0xFF0A7D5A.toInt(), l.success)
    }

    @Test
    fun runtimeColorsReadTheTokens() {
        assertEquals(l.heroStart, LatiFiColors.Light.heroStart.toArgbInt())
        assertEquals(d.repTrusted, LatiFiColors.Dark.repTrusted.toArgbInt())
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
        val a = (alpha * 255f + 0.5f).toInt()
        val r = (red * 255f + 0.5f).toInt()
        val g = (green * 255f + 0.5f).toInt()
        val b = (blue * 255f + 0.5f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
