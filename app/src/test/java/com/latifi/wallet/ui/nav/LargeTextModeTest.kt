package com.latifi.wallet.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeTextModeTest {
    @Test
    fun normalAndSlightlyLargeTextKeepTheBar() {
        assertFalse(isLargeTextMode(1.0f))
        assertFalse(isLargeTextMode(1.29f))
    }

    @Test
    fun fromOnePointThreeTheDrawerTakesOver() {
        assertTrue(isLargeTextMode(1.3f))
        assertTrue(isLargeTextMode(1.5f))
        assertTrue(isLargeTextMode(2.0f))
    }

    @Test
    fun smallerThanNormalTextKeepsTheBar() {
        assertFalse(isLargeTextMode(0.85f))
    }

    @Test
    fun theThresholdIsTheOneOfTheUiSpec() {
        assertEquals(1.3f, LARGE_TEXT_SCALE, 0f)
    }
}
