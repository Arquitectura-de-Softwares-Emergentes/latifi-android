package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

class TokenAmountTest {
    private val enUs: Locale = Locale.forLanguageTag("en-US")

    private fun valid(text: String): BigInteger {
        val result = TokenAmount.parse(text)
        assertTrue("expected valid for '$text', got $result", result is AmountParse.Valid)
        return (result as AmountParse.Valid).amount.baseUnits
    }

    private fun reason(text: String): ParseFailure {
        val result = TokenAmount.parse(text)
        assertTrue("expected invalid for '$text', got $result", result is AmountParse.Invalid)
        return (result as AmountParse.Invalid).reason
    }

    @Test
    fun parsesWholeAndDecimalAmountsToBaseUnits() {
        assertEquals(BigInteger.valueOf(100_000_000L), valid("100"))
        assertEquals(BigInteger.valueOf(250_500_000L), valid("250.5"))
        assertEquals(BigInteger.valueOf(1L), valid("0.000001"))
        assertEquals(BigInteger.valueOf(123_456_789L), valid("123.456789"))
        assertEquals(BigInteger.ZERO, valid("0"))
    }

    @Test
    fun acceptsACommaAsTheDecimalSeparator() {
        assertEquals(BigInteger.valueOf(12_340_000L), valid("12,34"))
    }

    @Test
    fun acceptsTrailingAndLeadingSeparatorsWhileTyping() {
        assertEquals(BigInteger.valueOf(5_000_000L), valid("5."))
        assertEquals(BigInteger.valueOf(500_000L), valid(".5"))
    }

    @Test
    fun rejectsEmptyAndBlankText() {
        assertEquals(ParseFailure.EMPTY, reason(""))
        assertEquals(ParseFailure.EMPTY, reason("   "))
    }

    @Test
    fun rejectsMalformedText() {
        for (text in listOf("abc", "1.2.3", "1,2.3", "-5", "+5", "1 000", "1,000.50x", ".", ",", " 5", "5 ", "1e3", "١٢")) {
            assertEquals("text '$text'", ParseFailure.MALFORMED, reason(text))
        }
    }

    @Test
    fun rejectsMoreThanSixDecimals() {
        assertEquals(ParseFailure.TOO_MANY_DECIMALS, reason("1.0000001"))
        assertEquals(ParseFailure.TOO_MANY_DECIMALS, reason("0,1234567"))
    }

    @Test
    fun formatsTwoDecimalsWithGroupingInEnglish() {
        assertEquals("250.00 tUSD", TokenAmount(BigInteger.valueOf(250_000_000L)).format(enUs))
        assertEquals("1,000.00 tUSD", TokenAmount(BigInteger.valueOf(1_000_000_000L)).format(enUs))
        assertEquals("0.00 tUSD", TokenAmount(BigInteger.ZERO).format(enUs))
        assertEquals("10,000.00 tUSD", TokenAmount.ofTokens(10_000).format(enUs))
    }

    @Test
    fun displayRoundingIsHalfEven() {
        // 0.125 sits on a tie: the even neighbour is 0.12. 0.135 goes to 0.14.
        assertEquals("0.12 tUSD", TokenAmount(BigInteger.valueOf(125_000L)).format(enUs))
        assertEquals("0.14 tUSD", TokenAmount(BigInteger.valueOf(135_000L)).format(enUs))
        assertEquals("100.82 tUSD", TokenAmount(BigInteger.valueOf(100_821_917L)).format(enUs))
    }

    @Test
    fun formatExactKeepsUpToSixDecimalsAndNeverFewerThanTwo() {
        assertEquals("50.410958 tUSD", TokenAmount(BigInteger.valueOf(50_410_958L)).formatExact(enUs))
        assertEquals("250.00 tUSD", TokenAmount(BigInteger.valueOf(250_000_000L)).formatExact(enUs))
        assertEquals("250.50 tUSD", TokenAmount(BigInteger.valueOf(250_500_000L)).formatExact(enUs))
        assertEquals("0.000001 tUSD", TokenAmount(BigInteger.ONE).formatExact(enUs))
        assertEquals("1,000.1234 tUSD", TokenAmount(BigInteger.valueOf(1_000_123_400L)).formatExact(enUs))
    }

    @Test
    fun aNegativeAmountCannotExist() {
        try {
            TokenAmount(BigInteger.valueOf(-1))
            throw AssertionError("expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
