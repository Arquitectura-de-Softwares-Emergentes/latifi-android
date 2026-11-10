package com.latifi.wallet.domain.rates

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.RateSnapshot
import java.math.BigDecimal
import java.time.Instant

/** The authoritative conversion of one amount (`GET /exchange-rate/convert`). */
data class ConvertedAmount(
    val value: BigDecimal,
    val rate: BigDecimal,
    val asOf: Instant,
    val stale: Boolean,
)

/**
 * The exchange rate reads of the API. [rates] feeds the list screens, which convert on the
 * client. [convert] is for the confirmation screens, which show the server's number.
 * A missing rate fails with `AppError.RateUnavailable`.
 */
interface ExchangeRateRepository {
    suspend fun rates(): Result<RateSnapshot>

    suspend fun convert(amount: TokenAmount, currency: LocalCurrency): Result<ConvertedAmount>
}
