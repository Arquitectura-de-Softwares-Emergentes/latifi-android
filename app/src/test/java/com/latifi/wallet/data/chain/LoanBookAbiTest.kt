package com.latifi.wallet.data.chain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Hash
import java.math.BigInteger

class LoanBookAbiTest {
    private fun word(value: Long): String = BigInteger.valueOf(value).toString(16).padStart(64, '0')

    private fun selector(signature: String): String = Hash.sha3String(signature).substring(0, 10)

    @Test
    fun requestLoanStartsWithItsSelector() {
        val data = LoanBookAbi.requestLoan(BigInteger.valueOf(100_000_000L), 1000, 30, 2)
        assertTrue(data, data.startsWith("0x430a09d0"))
        assertEquals("0x430a09d0", selector("requestLoan(uint256,uint16,uint32,uint8)"))
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

    @Test
    fun fundCalldataIsTheSelectorPlusTheLoanId() {
        assertEquals("0xca1d209d" + word(7), LoanBookAbi.fund(BigInteger.valueOf(7)))
        assertEquals("0xca1d209d", selector("fund(uint256)"))
    }

    @Test
    fun repayCalldataIsTheSelectorPlusTwoWords() {
        assertEquals(
            "0xd8aed145" + word(7) + word(50_410_958L),
            LoanBookAbi.repay(BigInteger.valueOf(7), BigInteger.valueOf(50_410_958L)),
        )
        assertEquals("0xd8aed145", selector("repay(uint256,uint256)"))
    }

    @Test
    fun repayRefusesAZeroPayment() {
        try {
            LoanBookAbi.repay(BigInteger.ONE, BigInteger.ZERO)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun loanCountIsJustTheSelector() {
        assertEquals("0xce63094d", LoanBookAbi.loanCount())
        assertEquals("0xce63094d", selector("loanCount()"))
    }

    @Test
    fun nextInstallmentCalldataIsTheSelectorPlusTheLoanId() {
        assertEquals("0x3b8dbd88" + word(3), LoanBookAbi.nextInstallment(BigInteger.valueOf(3)))
        assertEquals("0x3b8dbd88", selector("nextInstallment(uint256)"))
    }

    @Test
    fun decodeNextInstallmentReadsIndexDueTimeAndAmount() {
        val payload = "0x" + word(2) + word(1_800_000_000L) + word(341_552_511L)
        val next = LoanBookAbi.decodeNextInstallment(payload)
        assertEquals(NextInstallment(2, BigInteger.valueOf(1_800_000_000L), BigInteger.valueOf(341_552_511L)), next)
    }

    @Test
    fun decodeNextInstallmentReturnsNullForShortOrEmptyData() {
        assertNull(LoanBookAbi.decodeNextInstallment("0x"))
        assertNull(LoanBookAbi.decodeNextInstallment("0x" + word(1) + word(2)))
        assertNull(LoanBookAbi.decodeNextInstallment("0xzz"))
    }
}
