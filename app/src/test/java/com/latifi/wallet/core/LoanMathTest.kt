package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.Instant

class LoanMathTest {
    private fun big(value: Long): BigInteger = BigInteger.valueOf(value)

    @Test
    fun adr0004ExampleGivesTheContractInterestAndTotal() {
        val principal = big(100_000_000L)
        assertEquals(big(821_917L), LoanMath.interest(principal, 1000, 30))
        assertEquals(big(100_821_917L), LoanMath.totalDue(principal, 1000, 30))
    }

    @Test
    fun adr0004ExampleSplitsIntoTwoInstallments() {
        assertEquals(
            listOf(big(50_410_958L), big(50_410_959L)),
            LoanMath.installmentAmounts(big(100_821_917L), 2),
        )
    }

    @Test
    fun loanBookExampleMatchesTheContractDocument() {
        // LOANBOOK.md worked example: 1000 tokens, 1000 bps, 90 days, three installments.
        val total = LoanMath.totalDue(big(1_000_000_000L), 1000, 90)
        assertEquals(big(1_024_657_534L), total)
        assertEquals(
            listOf(big(341_552_511L), big(341_552_511L), big(341_552_512L)),
            LoanMath.installmentAmounts(total, 3),
        )
    }

    @Test
    fun oneInstallmentCarriesTheWholeTotal() {
        assertEquals(listOf(big(100_821_917L)), LoanMath.installmentAmounts(big(100_821_917L), 1))
    }

    @Test
    fun sixInstallmentsLetTheLastOneAbsorbTheRemainder() {
        val amounts = LoanMath.installmentAmounts(big(100_000_003L), 6)
        assertEquals(6, amounts.size)
        // floor(100000003 / 6) = 16666667, remainder 1.
        assertEquals(List(5) { big(16_666_667L) }, amounts.take(5))
        assertEquals(big(16_666_668L), amounts.last())
        assertEquals(big(100_000_003L), amounts.fold(BigInteger.ZERO, BigInteger::add))
    }

    @Test
    fun theLastInstallmentIsLargerByAtMostNMinusOne() {
        for (n in 1..6) {
            val total = big(100_000_000L + n)
            val amounts = LoanMath.installmentAmounts(total, n)
            assertEquals(total, amounts.fold(BigInteger.ZERO, BigInteger::add))
            assertTrue(amounts.last() - amounts.first() <= big((n - 1).toLong()))
        }
    }

    @Test
    fun aTotalSmallerThanTheCountPutsEverythingOnTheLastInstallment() {
        val amounts = LoanMath.installmentAmounts(BigInteger.ONE, 6)
        assertEquals(List(5) { BigInteger.ZERO } + BigInteger.ONE, amounts)
    }

    @Test
    fun interestFloorsAtTheDivisorBoundary() {
        // principal * bps * termDays = 3650000 gives exactly one base unit, one less gives zero.
        assertEquals(big(1), LoanMath.interest(big(3_650_000L), 1, 1))
        assertEquals(big(0), LoanMath.interest(big(3_649_999L), 1, 1))
        assertEquals(big(0), LoanMath.interest(big(1), 1000, 90))
    }

    @Test
    fun interestDoesNotOverflowAtTheLargestUiRequest() {
        // 10,000 tokens at 50 percent for 365 days adds half the principal.
        val principal = big(10_000_000_000L)
        assertEquals(big(5_000_000_000L), LoanMath.interest(principal, 5000, 365))
    }

    @Test
    fun dueDatesUseIntegerDivisionOfTheTerm() {
        val funded = Instant.ofEpochSecond(1_700_000_000L)
        // termDays 10, n = 3: k * 864000 / 3 = 288000, 576000 and 864000 seconds.
        assertEquals(funded.plusSeconds(288_000L), LoanMath.dueAt(funded, 10, 1, 3))
        assertEquals(funded.plusSeconds(576_000L), LoanMath.dueAt(funded, 10, 2, 3))
        assertEquals(funded.plusSeconds(864_000L), LoanMath.dueAt(funded, 10, 3, 3))
        // termDays 1, n = 6: 86400 / 6 is exact.
        assertEquals(funded.plusSeconds(14_400L), LoanMath.dueAt(funded, 1, 1, 6))
    }

    @Test
    fun theLastInstallmentFallsExactlyOnTheTermEnd() {
        val funded = Instant.ofEpochSecond(1_700_000_000L)
        for (n in 1..6) {
            assertEquals(funded.plusSeconds(30L * 86_400L), LoanMath.dueAt(funded, 30, n, n))
        }
    }

    @Test
    fun dueAtTruncatesTowardZeroLikeTheContract() {
        val funded = Instant.ofEpochSecond(0)
        // 1 * 1 * 86400 / 7 = 12342.857..., the contract keeps 12342.
        assertEquals(funded.plusSeconds(12_342L), LoanMath.dueAt(funded, 1, 1, 7))
    }

    @Test
    fun rateToBpsReadsPercentsWithTwoDecimals() {
        assertEquals(1000, LoanMath.rateToBps("10.00"))
        assertEquals(1000, LoanMath.rateToBps("10"))
        assertEquals(1, LoanMath.rateToBps("0.01"))
        assertEquals(5000, LoanMath.rateToBps("50.00"))
        assertEquals(1050, LoanMath.rateToBps("10,5"))
        assertEquals(0, LoanMath.rateToBps("0"))
    }

    @Test
    fun rateToBpsRejectsMoreThanTwoDecimalsAndBadText() {
        assertNull(LoanMath.rateToBps("10.001"))
        assertNull(LoanMath.rateToBps("0.005"))
        assertNull(LoanMath.rateToBps(""))
        assertNull(LoanMath.rateToBps("abc"))
        assertNull(LoanMath.rateToBps("-1"))
        assertNull(LoanMath.rateToBps("1.2.3"))
        assertNull(LoanMath.rateToBps("99999999999999999999"))
    }

    @Test
    fun parseRateKeepsTheReasonForTheForm() {
        assertEquals(RateParse.Invalid(ParseFailure.EMPTY), LoanMath.parseRate(""))
        assertEquals(RateParse.Invalid(ParseFailure.TOO_MANY_DECIMALS), LoanMath.parseRate("1.234"))
        assertEquals(RateParse.Invalid(ParseFailure.MALFORMED), LoanMath.parseRate("x"))
        assertEquals(RateParse.Valid(big(1000)), LoanMath.parseRate("10.00"))
    }
}
