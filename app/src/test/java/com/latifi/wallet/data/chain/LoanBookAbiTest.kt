package com.latifi.wallet.data.chain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class LoanBookAbiTest {
    private fun word(value: Long): String = BigInteger.valueOf(value).toString(16).padStart(64, '0')

    @Test
    fun requestLoanStartsWithItsSelector() {
        val data = LoanBookAbi.requestLoan(BigInteger.valueOf(100_000_000L), 1000, 30, 2)
        assertTrue(data, data.startsWith("0x430a09d0"))
    }

    @Test
    fun requestLoanCalldataIsTheSelectorPlusFourWords() {
        val expected = "0x" + "430a09d0" + word(100_000_000L) + word(1000) + word(30) + word(2)
        assertEquals(expected, LoanBookAbi.requestLoan(BigInteger.valueOf(100_000_000L), 1000, 30, 2))
        assertEquals(2 + 8 + 4 * 64, expected.length)
    }

    @Test
    fun requestLoanOutputIsLowercaseHex() {
        val data = LoanBookAbi.requestLoan(BigInteger.valueOf(255L), 5000, 365, 6)
        assertEquals(data.lowercase(), data)
    }
}
