package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PercentFormatTest {
    private val en = Locale.forLanguageTag("en-US")
    private val de = Locale.GERMANY

    @Test
    fun aWholePercentLosesItsZeros() {
        assertEquals("10", PercentFormat.fromBasisPoints(1000, en))
        assertEquals("50", PercentFormat.fromBasisPoints(5000, en))
    }

    @Test
    fun decimalsAreKeptUpToTwoPlaces() {
        assertEquals("12.5", PercentFormat.fromBasisPoints(1250, en))
        assertEquals("0.01", PercentFormat.fromBasisPoints(1, en))
        assertEquals("12.34", PercentFormat.fromBasisPoints(1234, en))
    }

    @Test
    fun theDecimalSymbolFollowsTheLocale() {
        assertEquals("12,5", PercentFormat.fromBasisPoints(1250, de))
    }
}
