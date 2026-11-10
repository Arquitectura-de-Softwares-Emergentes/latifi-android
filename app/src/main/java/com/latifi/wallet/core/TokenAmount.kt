package com.latifi.wallet.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/** Why a typed number was rejected. */
enum class ParseFailure { EMPTY, MALFORMED, TOO_MANY_DECIMALS }

/** Outcome of parsing the amount a person typed. */
sealed interface AmountParse {
    data class Valid(val amount: TokenAmount) : AmountParse
    data class Invalid(val reason: ParseFailure) : AmountParse
}

/**
 * An amount of the lending token in base units (6 decimals). It wraps a [BigInteger] so no
 * rounding path exists between the text a person types and the number a wallet signs.
 */
@JvmInline
value class TokenAmount(val baseUnits: BigInteger) {

    init {
        require(baseUnits.signum() >= 0) { "A token amount is never negative" }
    }

    /** Two decimals, half-even, with the grouping and decimal symbols of [locale]: `250.00 tUSD`. */
    fun format(locale: Locale): String = formatNumber(locale, minDecimals = 2, maxDecimals = 2) + SUFFIX

    /**
     * Up to six decimals with trailing zeros trimmed but never fewer than two. This is the text
     * for an exact approval, where rounding would hide part of the amount.
     */
    fun formatExact(locale: Locale): String =
        formatNumber(locale, minDecimals = 2, maxDecimals = DECIMALS) + SUFFIX

    /** The amount as a decimal number of whole tokens, exact. */
    fun toDecimal(): BigDecimal = BigDecimal(baseUnits, DECIMALS)

    private fun formatNumber(locale: Locale, minDecimals: Int, maxDecimals: Int): String {
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        format.isGroupingUsed = true
        format.roundingMode = RoundingMode.HALF_EVEN
        format.minimumFractionDigits = minDecimals
        format.maximumFractionDigits = maxDecimals
        // A BigDecimal goes through the exact path of DecimalFormat, never through a binary number.
        return format.format(toDecimal())
    }

    companion object {
        /** Decimals of the lending token. */
        const val DECIMALS = 6

        /** One whole token in base units. */
        val ONE_TOKEN: BigInteger = BigInteger.valueOf(1_000_000L)

        /** Display suffix. It is a constant because the UI marks it as not translatable. */
        const val SUFFIX = " tUSD"

        /**
         * Parses digits with at most one separator (a dot or a comma) and at most six decimals.
         * No sign, no grouping and no padding are accepted. A lone separator is malformed, while
         * `5.` and `.5` count as 5 and 0.5 so a person can type the number step by step.
         */
        fun parse(text: String): AmountParse =
            when (val parsed = DecimalInput.parse(text, DECIMALS)) {
                is DecimalInput.Result.Ok ->
                    AmountParse.Valid(TokenAmount(parsed.scaled(DECIMALS)))
                is DecimalInput.Result.Fail -> AmountParse.Invalid(parsed.reason)
            }

        /** Builds an amount from a whole number of tokens. */
        fun ofTokens(tokens: Long): TokenAmount = TokenAmount(ONE_TOKEN.multiply(BigInteger.valueOf(tokens)))
    }
}

/** Shared strict reader of typed decimal numbers (amounts and rates). */
internal object DecimalInput {
    sealed interface Result {
        /** [whole] and [fraction] hold ASCII digits only, [fraction] without trailing padding. */
        data class Ok(val whole: String, val fraction: String) : Result {
            /** The value times ten to the power of [decimals], as an integer. */
            fun scaled(decimals: Int): BigInteger {
                val padded = fraction.padEnd(decimals, '0')
                return BigInteger((whole.ifEmpty { "0" }) + padded)
            }
        }

        data class Fail(val reason: ParseFailure) : Result
    }

    private val DIGITS = Regex("[0-9]*")

    fun parse(text: String, maxDecimals: Int): Result {
        if (text.isBlank()) return Result.Fail(ParseFailure.EMPTY)
        val separators = text.count { it == '.' || it == ',' }
        if (separators > 1) return Result.Fail(ParseFailure.MALFORMED)
        val parts = text.split('.', ',')
        val whole = parts[0]
        val fraction = if (parts.size > 1) parts[1] else ""
        if (!DIGITS.matches(whole) || !DIGITS.matches(fraction)) {
            return Result.Fail(ParseFailure.MALFORMED)
        }
        if (whole.isEmpty() && fraction.isEmpty()) return Result.Fail(ParseFailure.MALFORMED)
        if (fraction.length > maxDecimals) return Result.Fail(ParseFailure.TOO_MANY_DECIMALS)
        return Result.Ok(whole, fraction)
    }
}
