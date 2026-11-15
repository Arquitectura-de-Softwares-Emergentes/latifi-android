package com.latifi.wallet.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Report 6.1.1 typography: six sizes, four weights, 1.5 line height on body text, tnum on amounts. */
class TypographyTokensTest {
    private val roles = LatiFiType.roles

    private val materialSlots: List<TextStyle> = LatiFiTypography.let {
        listOf(
            it.displayLarge, it.displayMedium, it.displaySmall,
            it.headlineLarge, it.headlineMedium, it.headlineSmall,
            it.titleLarge, it.titleMedium, it.titleSmall,
            it.bodyLarge, it.bodyMedium, it.bodySmall,
            it.labelLarge, it.labelMedium, it.labelSmall,
        )
    }

    @Test
    fun hasSevenRoles() {
        assertEquals(7, roles.size)
    }

    @Test
    fun usesExactlySixSizes() {
        assertEquals(setOf(32f, 24f, 18f, 16f, 14f, 12f), roles.map { it.fontSize.value }.toSet())
    }

    @Test
    fun usesExactlyFourWeights() {
        assertEquals(setOf(400, 500, 600, 700), roles.map { it.fontWeight!!.weight }.toSet())
    }

    @Test
    fun rolesMatchTheSpecTable() {
        // Size, weight and line height of each role, in the order of the UI-SPEC table.
        val expected = listOf(
            Triple(32f, 700, 40f),
            Triple(24f, 600, 32f),
            Triple(18f, 600, 24f),
            Triple(16f, 400, 24f),
            Triple(14f, 400, 21f),
            Triple(14f, 600, 21f),
            Triple(12f, 500, 18f),
        )
        val actual = roles.map { Triple(it.fontSize.value, it.fontWeight!!.weight, it.lineHeight.value) }
        assertEquals(expected, actual)
    }

    @Test
    fun bodyLabelAndCaptionKeepLineHeightAtOnePointFive() {
        listOf(LatiFiType.BodyL, LatiFiType.BodyM, LatiFiType.Label, LatiFiType.Caption).forEach {
            assertEquals(it.fontSize.value * 1.5f, it.lineHeight.value, 0.001f)
        }
    }

    @Test
    fun displayHeadlineAndTitleUseSoraAndTheRestUseInter() {
        listOf(LatiFiType.Display, LatiFiType.Headline, LatiFiType.Title).forEach {
            assertEquals(LatiFiFonts.Sora, it.fontFamily)
        }
        listOf(LatiFiType.BodyL, LatiFiType.BodyM, LatiFiType.Label, LatiFiType.Caption).forEach {
            assertEquals(LatiFiFonts.Inter, it.fontFamily)
        }
    }

    @Test
    fun amountStylesCarryTabularFigures() {
        assertTrue(LatiFiType.Amount.all.isNotEmpty())
        LatiFiType.Amount.all.forEach { assertEquals("tnum", it.fontFeatureSettings) }
    }

    @Test
    fun amountStylesKeepTheSizeOfTheirRole() {
        assertEquals(LatiFiType.Display.fontSize, LatiFiType.Amount.Display.fontSize)
        assertEquals(LatiFiType.Headline.fontSize, LatiFiType.Amount.Headline.fontSize)
        assertEquals(LatiFiType.BodyM.fontSize, LatiFiType.Amount.BodyM.fontSize)
    }

    @Test
    fun everyMaterialSlotMapsOntoARole() {
        // No component may fall back to a default Material size or weight.
        materialSlots.forEach {
            assertNotNull(it.fontFamily)
            assertTrue("size ${it.fontSize.value}", it.fontSize.value in setOf(32f, 24f, 18f, 16f, 14f, 12f))
            assertTrue("weight ${it.fontWeight}", it.fontWeight in setOf(
                FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
            ))
        }
    }
}
