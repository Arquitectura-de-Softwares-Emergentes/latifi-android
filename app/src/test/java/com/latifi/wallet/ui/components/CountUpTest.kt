package com.latifi.wallet.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class CountUpTest {
    private val zero = BigInteger.ZERO
    private val target = BigInteger("1000000000")

    @Test
    fun endpointsAreExact() {
        assertEquals(zero, interpolateUnits(zero, target, 0f))
        assertEquals(target, interpolateUnits(zero, target, 1f))
        assertEquals(target, interpolateUnits(zero, target, 1.5f))
        assertEquals(zero, interpolateUnits(zero, target, -0.2f))
    }

    @Test
    fun growsWithoutOvershoot() {
        var last = zero
        for (step in 0..100) {
            val value = interpolateUnits(zero, target, step / 100f)
            assertTrue(value >= last)
            assertTrue(value <= target)
            last = value
        }
    }

    @Test
    fun countsDownToo() {
        val value = interpolateUnits(target, zero, 0.5f)
        assertEquals(BigInteger("500000000"), value)
    }

    @Test
    fun bigAmountsNeverPassThroughAFloat() {
        val big = BigInteger("123456789012345678901234567890")
        assertEquals(big, interpolateUnits(zero, big, 1f))
    }
}
