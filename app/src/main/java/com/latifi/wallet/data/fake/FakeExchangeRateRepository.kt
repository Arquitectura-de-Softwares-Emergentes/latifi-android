package com.latifi.wallet.data.fake

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.rates.ConvertedAmount
import com.latifi.wallet.domain.rates.ExchangeRateRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration

/**
 * Fixed rates for the demo build and for tests. [stale] ages the quotes by three hours and marks
 * them stale. [unavailable] answers like the API's 503 RATE_UNAVAILABLE. [ratesCalls] counts the
 * reads, so a test can prove that many conversions cost one call.
 */
class FakeExchangeRateRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val table: Map<LocalCurrency, BigDecimal> = DEFAULT_RATES,
) : ExchangeRateRepository {
    var stale: Boolean = false
    var unavailable: Boolean = false

    /** The next call fails with this error, once. */
    var failNext: AppError? = null

    var ratesCalls = 0
        private set

    override suspend fun rates(): Result<RateSnapshot> {
        ratesCalls += 1
        failure()?.let { return Result.failure(AppException(it)) }
        val asOf = clock.instant().let { if (stale) it.minus(Duration.ofHours(3)) else it }
        val quotes = table.map { (currency, rate) -> RateQuote("USD", currency.code, rate, asOf, stale) }
        return Result.success(RateSnapshot(quotes, asOf, stale))
    }

    override suspend fun convert(amount: TokenAmount, currency: LocalCurrency): Result<ConvertedAmount> {
        failure()?.let { return Result.failure(AppException(it)) }
        val rate = table[currency] ?: return Result.failure(AppException(AppError.RateUnavailable))
        val asOf = clock.instant().let { if (stale) it.minus(Duration.ofHours(3)) else it }
        return Result.success(ConvertedAmount(currency.convert(amount, rate), rate, asOf, stale))
    }

    private fun failure(): AppError? {
        failNext?.let { error ->
            failNext = null
            return error
        }
        return if (unavailable) AppError.RateUnavailable else null
    }

    companion object {
        val DEFAULT_RATES: Map<LocalCurrency, BigDecimal> = mapOf(
            LocalCurrency.PEN to BigDecimal("3.75"),
            LocalCurrency.COP to BigDecimal("3900.00"),
            LocalCurrency.MXN to BigDecimal("17.50"),
            LocalCurrency.ARS to BigDecimal("1000.00"),
            LocalCurrency.BRL to BigDecimal("5.00"),
            LocalCurrency.CLP to BigDecimal("950.00"),
        )
    }
}
