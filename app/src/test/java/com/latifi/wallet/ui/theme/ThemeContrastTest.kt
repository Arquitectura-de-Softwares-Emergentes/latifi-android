package com.latifi.wallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Holds every pair of the UI-SPEC Contrast Table (WCAG 2.1 relative luminance). The colors
 * come from the real token constants, so a token edit that breaks a pair fails the build.
 * Each row asserts the ratio against its threshold and against the value printed in the
 * table, within [TOLERANCE].
 */
class ThemeContrastTest {
    private data class Pair(
        val name: String,
        val foreground: Int,
        val background: Int,
        val threshold: Double,
        val printed: Double,
    )

    private val text = 4.5
    private val nonText = 3.0

    private val l = ColorTokens.Light
    private val d = ColorTokens.Dark

    private val passing = listOf(
        // Light theme
        Pair("light onSurface on background", l.onSurface, l.background, text, 17.75),
        Pair("light onSurface on surface", l.onSurface, l.surface, text, 16.54),
        Pair("light onSurfaceVariant on background", l.onSurfaceVariant, l.background, text, 6.25),
        Pair("light onSurfaceVariant on surface", l.onSurfaceVariant, l.surface, text, 5.83),
        Pair("light onPrimary on primary (button)", l.onPrimary, l.primary, text, 16.52),
        Pair("light link on background (links)", l.link, l.background, text, 10.27),
        Pair("light focusRing on background", l.focusRing, l.background, nonText, 10.27),
        Pair("light link on surface", l.link, l.surface, text, 9.57),
        Pair("light primary on secondaryContainer (selected chip)", l.primary, l.secondaryContainer, text, 13.12),
        Pair("light link on secondaryContainer (chip check)", l.link, l.secondaryContainer, nonText, 8.16),
        Pair("light success on background", l.success, l.background, text, 5.13),
        Pair("light success on successContainer", l.success, l.successContainer, text, 4.53),
        Pair("light warning on background", l.warning, l.background, text, 5.02),
        Pair("light warning on warningContainer", l.warning, l.warningContainer, text, 4.58),
        Pair("light error on background", l.error, l.background, text, 5.62),
        Pair("light error on errorContainer", l.error, l.errorContainer, text, 4.92),
        Pair("light link on infoContainer", l.link, l.infoContainer, text, 8.88),
        Pair("light outline on background (control border)", l.outline, l.background, nonText, 3.68),
        Pair("light outline on surface (control border)", l.outline, l.surface, nonText, 3.43),
        Pair("light primary on coral", l.primary, ColorTokens.Coral500, text, 5.86),
        // Dark theme
        Pair("dark onSurface on background", d.onSurface, d.background, text, 17.91),
        Pair("dark onSurface on surface", d.onSurface, d.surface, text, 15.93),
        Pair("dark onSurfaceVariant on background", d.onSurfaceVariant, d.background, text, 9.16),
        Pair("dark onSurfaceVariant on surface", d.onSurfaceVariant, d.surface, text, 8.15),
        Pair("dark onPrimary on primary (button)", d.onPrimary, d.primary, text, 16.27),
        Pair("dark primary on background (progress)", d.primary, d.background, nonText, 16.27),
        Pair("dark focusRing on background", d.focusRing, d.background, nonText, 16.27),
        Pair("dark primary on surface", d.primary, d.surface, nonText, 14.47),
        Pair("dark primary on secondaryContainer (chip check)", d.primary, d.secondaryContainer, nonText, 10.80),
        Pair("dark onSurface on secondaryContainer (chip text)", d.onSurface, d.secondaryContainer, text, 11.89),
        Pair("dark link on background", d.link, d.background, text, 9.58),
        Pair("dark link on surface", d.link, d.surface, text, 8.52),
        Pair("dark link on infoContainer", d.link, d.infoContainer, text, 7.16),
        Pair("dark success on surface", d.success, d.surface, text, 8.95),
        Pair("dark success on successContainer", d.success, d.successContainer, text, 7.17),
        Pair("dark warning on surface", d.warning, d.surface, text, 8.38),
        Pair("dark warning on warningContainer", d.warning, d.warningContainer, text, 6.78),
        Pair("dark error on surface", d.error, d.surface, text, 7.49),
        Pair("dark error on errorContainer", d.error, d.errorContainer, text, 6.70),
        Pair("dark outline on background (control border)", d.outline, d.background, nonText, 5.29),
        Pair("dark outline on surface (control border)", d.outline, d.surface, nonText, 4.71),
        Pair("dark coral on surface (icon)", ColorTokens.Coral500, d.surface, nonText, 6.07),
    )

    /** Rows of the table that are documented non-passes: decorative only, never text or a control border. */
    private val decorativeOnly = listOf(
        Pair("light outlineDecor on background (card border)", l.outlineDecor, l.background, nonText, 1.29),
        Pair("light coral on background", ColorTokens.Coral500, l.background, nonText, 2.82),
    )

    @Test
    fun everyPairMeetsItsThreshold() {
        val failures = passing.filter { ratio(it.foreground, it.background) < it.threshold }.map {
            "${it.name}: ${fmt(ratio(it.foreground, it.background))} needs ${it.threshold}"
        }
        assertTrue("Pairs below threshold:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyRatioMatchesTheValuePrintedInTheTable() {
        val drift = (passing + decorativeOnly).filter {
            abs(ratio(it.foreground, it.background) - it.printed) > TOLERANCE
        }.map {
            "${it.name}: computed ${fmt(ratio(it.foreground, it.background))}, table says ${it.printed}"
        }
        assertTrue("Ratios that drifted from the table:\n" + drift.joinToString("\n"), drift.isEmpty())
    }

    @Test
    fun decorativeOnlyPairsStayBelowTheirThreshold() {
        // These pairs are allowed in the design only as decoration. If one starts to pass,
        // the table and the usage rules need a review.
        decorativeOnly.forEach {
            val r = ratio(it.foreground, it.background)
            assertTrue("${it.name} is ${fmt(r)} and must stay below ${it.threshold}", r < it.threshold)
        }
    }

    @Test
    fun tableHoldsEveryRowOfTheUiSpec() {
        // The table has 36 rows. Rows that print two ratios are split and the focus ring gets its
        // own row, which gives 44 pairs, 2 of them decorative only.
        assertEquals(44, passing.size + decorativeOnly.size)
    }

    @Test
    fun ratioFormulaMatchesKnownValues() {
        assertEquals(21.0, ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.001)
        assertEquals(1.0, ratio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.001)
    }

    private fun fmt(value: Double) = "%.2f".format(value)

    private companion object {
        const val TOLERANCE = 0.02

        fun ratio(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            val hi = maxOf(la, lb)
            val lo = minOf(la, lb)
            return (hi + 0.05) / (lo + 0.05)
        }

        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
    }
}
