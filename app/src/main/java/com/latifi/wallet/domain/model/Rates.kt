package com.latifi.wallet.domain.model

import com.latifi.wallet.core.LocalCurrency
import java.math.BigDecimal
import java.time.Instant

/** Units of [quote] currency per one US dollar (OpenAPI 0.2.0 `RateQuote`). */
data class RateQuote(
    val base: String,
    val quote: String,
    val rate: BigDecimal,
    val asOf: Instant,
    val stale: Boolean,
)

/** The rate list of one fetch. [asOf] is the instant of the oldest quote, [stale] is true if any quote is stale. */
data class RateSnapshot(
    val quotes: List<RateQuote>,
    val asOf: Instant,
    val stale: Boolean,
) {
    /** The quote for [currency], or null when the list does not carry it. */
    fun quoteFor(currency: LocalCurrency): RateQuote? = quotes.firstOrNull { it.quote == currency.code }
}
