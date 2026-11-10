package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale

class LocalCurrencyTest {
    private val enUs: Locale = Locale.forLanguageTag("en-US")
    private val es419: Locale = Locale.forLanguageTag("es-419")

    private fun tokens(whole: Long): TokenAmount = TokenAmount.ofTokens(whole)

    @Test
    fun twoHundredFiftyTokensAtThreeSeventyFiveGiveNineHundredThirtySevenFifty() {
        val rate = BigDecimal("3.75")
        assertEquals(BigDecimal("937.50"), LocalCurrency.PEN.convert(tokens(250), rate))
        assertEquals("S/ 937.50", LocalCurrency.PEN.formatEstimate(tokens(250), rate, enUs))
    }

    @Test
    fun theCodeTableHasTheSixCurrenciesWithTheirSymbolsAndDigits() {
        assertEquals(
            listOf("PEN", "COP", "MXN", "ARS", "BRL", "CLP"),
            LocalCurrency.entries.map { it.code },
        )
        assertEquals(
            listOf("S/", "$", "$", "$", "R$", "$"),
            LocalCurrency.entries.map { it.symbol },
        )
        assertEquals(listOf(2, 2, 2, 2, 2, 0), LocalCurrency.entries.map { it.fractionDigits })
    }

    @Test
    fun conversionRoundsHalfEvenAtATie() {
        // 0.5 token at rate 0.25 is 0.125: the even neighbour at two decimals is 0.12.
        val half = TokenAmount(BigInteger.valueOf(500_000L))
        assertEquals(BigDecimal("0.12"), LocalCurrency.PEN.convert(half, BigDecimal("0.25")))
        // 0.5 token at rate 0.27 is 0.135, which goes to the even neighbour 0.14.
        assertEquals(BigDecimal("0.14"), LocalCurrency.PEN.convert(half, BigDecimal("0.27")))
        // 0.5 token at rate 3 is 1.5, which CLP rounds to the even whole number 2, and 2.5 gives 2.
        assertEquals(BigDecimal("2"), LocalCurrency.CLP.convert(half, BigDecimal("3")))
        assertEquals(BigDecimal("2"), LocalCurrency.CLP.convert(half, BigDecimal("5")))
    }

    @Test
    fun clpHasNoDecimals() {
        val value = LocalCurrency.CLP.convert(tokens(250), BigDecimal("950.4"))
        assertEquals(BigDecimal("237600"), value)
        assertEquals("$ 237,600", LocalCurrency.CLP.format(value, enUs))
    }

    @Test
    fun largeCopAndArsValuesKeepEveryDigit() {
        val cop = LocalCurrency.COP.convert(tokens(10_000), BigDecimal("4123.456"))
        assertEquals(BigDecimal("41234560.00"), cop)
        assertEquals("$ 41,234,560.00", LocalCurrency.COP.format(cop, enUs))

        val ars = LocalCurrency.ARS.convert(tokens(10_000), BigDecimal("1050.5"))
        assertEquals(BigDecimal("10505000.00"), ars)
        assertEquals("$ 10,505,000.00", LocalCurrency.ARS.format(ars, enUs))
    }

    @Test
    fun conversionKeepsTheBaseUnitDigits() {
        // The product keeps all six base unit digits before it rounds to two decimals.
        val amount = TokenAmount(BigInteger.valueOf(100_821_917L))
        assertEquals(
            BigDecimal("100.821917").multiply(BigDecimal("3.7512")).setScale(2, RoundingMode.HALF_EVEN),
            LocalCurrency.PEN.convert(amount, BigDecimal("3.7512")),
        )
    }

    @Test
    fun brazilianRealUsesItsOwnSymbol() {
        assertEquals("R$ 1,234.50", LocalCurrency.BRL.format(BigDecimal("1234.5"), enUs))
    }

    @Test
    fun spanishLatinAmericaUsesTheJdkSymbolsForGroupingAndDecimals() {
        // Assumption A5: the JDK (CLDR) gives es-419 a comma for grouping and a dot for decimals.
        assertEquals("S/ 937.50", LocalCurrency.PEN.format(BigDecimal("937.50"), es419))
        assertEquals("$ 41,234,560.00", LocalCurrency.COP.format(BigDecimal("41234560.00"), es419))
        assertEquals("1,000.00 tUSD", TokenAmount.ofTokens(1_000).format(es419))
    }

    @Test
    fun unsupportedCodeReturnsNull() {
        assertNull(LocalCurrency.fromCode("USD"))
        assertNull(LocalCurrency.fromCode("pen"))
        assertNull(LocalCurrency.fromCode(""))
        assertEquals(LocalCurrency.CLP, LocalCurrency.fromCode("CLP"))
        assertEquals(LocalCurrency.PEN, LocalCurrency.fromCode("PEN"))
    }
}
