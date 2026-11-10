package com.latifi.wallet.domain.chain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class FeePolicyTest {
    private val threshold = FeePolicy.LOW_FEE_THRESHOLD_WEI

    @Test
    fun theThresholdIsTwoHundredthsOfOnePol() {
        assertEquals(BigInteger("20000000000000000"), threshold)
    }

    @Test
    fun aBalanceBelowTheThresholdIsLow() {
        assertTrue(FeePolicy.isLow(threshold - BigInteger.ONE))
    }

    @Test
    fun aBalanceEqualToTheThresholdIsNotLow() {
        assertFalse(FeePolicy.isLow(threshold))
    }

    @Test
    fun aBalanceAboveTheThresholdIsNotLow() {
        assertFalse(FeePolicy.isLow(threshold + BigInteger.ONE))
    }

    @Test
    fun anEmptyWalletIsLow() {
        assertTrue(FeePolicy.isLow(BigInteger.ZERO))
    }
}
