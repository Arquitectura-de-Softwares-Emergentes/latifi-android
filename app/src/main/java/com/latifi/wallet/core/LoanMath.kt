package com.latifi.wallet.core

import java.math.BigInteger
import java.time.Instant

/** Outcome of reading an interest rate typed as a percent a year. */
sealed interface RateParse {
    data class Valid(val bps: BigInteger) : RateParse
    data class Invalid(val reason: ParseFailure) : RateParse
}

/**
 * The loan arithmetic of the LoanBook contract (docs/LOANBOOK.md and ADR-0004), integer only.
 * Every function here must give the same number as the contract. The screens only display it.
 */
object LoanMath {
    /** 365 days times 10,000 basis points, the single divisor of the interest formula. */
    private val INTEREST_DIVISOR: BigInteger = BigInteger.valueOf(3_650_000L)
    private const val SECONDS_PER_DAY = 86_400L

    /** `floor(principal * bps * termDays / 3650000)`. */
    fun interest(principal: BigInteger, bps: Int, termDays: Int): BigInteger {
        require(principal.signum() >= 0 && bps >= 0 && termDays >= 0) { "Loan terms are never negative" }
        return principal
            .multiply(BigInteger.valueOf(bps.toLong()))
            .multiply(BigInteger.valueOf(termDays.toLong()))
            .divide(INTEREST_DIVISOR)
    }

    /** Principal plus [interest]. */
    fun totalDue(principal: BigInteger, bps: Int, termDays: Int): BigInteger =
        principal.add(interest(principal, bps, termDays))

    /**
     * Splits [totalDue] into [n] installments. The first n minus one are `floor(totalDue / n)`
     * and the last absorbs the remainder.
     */
    fun installmentAmounts(totalDue: BigInteger, n: Int): List<BigInteger> {
        require(n >= 1) { "A loan has at least one installment" }
        val count = BigInteger.valueOf(n.toLong())
        val base = totalDue.divide(count)
        val last = totalDue.subtract(base.multiply(BigInteger.valueOf((n - 1).toLong())))
        return List(n - 1) { base } + last
    }

    /**
     * Due time of installment [k] (1 based) of [n]: `fundedAt + k * termDays * 86400 / n` seconds
     * with integer division, so installment n falls exactly on `fundedAt + termDays` days.
     */
    fun dueAt(fundedAt: Instant, termDays: Int, k: Int, n: Int): Instant {
        require(n >= 1 && k in 1..n) { "Installment $k is outside 1..$n" }
        val offset = k.toLong() * termDays.toLong() * SECONDS_PER_DAY / n.toLong()
        return fundedAt.plusSeconds(offset)
    }

    /** Reads a percent with at most two decimals into basis points. A dot or a comma separates decimals. */
    fun parseRate(text: String): RateParse =
        when (val parsed = DecimalInput.parse(text, maxDecimals = 2)) {
            is DecimalInput.Result.Ok -> RateParse.Valid(parsed.scaled(2))
            is DecimalInput.Result.Fail -> RateParse.Invalid(parsed.reason)
        }

    /**
     * Basis points for a percent typed with up to two decimals (`10.00` gives 1000, `0.01` gives 1).
     * Returns null for anything malformed, empty, with more than two decimals or beyond the Int range.
     */
    fun rateToBps(text: String): Int? =
        (parseRate(text) as? RateParse.Valid)?.bps?.takeIf { it.bitLength() < Int.SIZE_BITS }?.toInt()
}
