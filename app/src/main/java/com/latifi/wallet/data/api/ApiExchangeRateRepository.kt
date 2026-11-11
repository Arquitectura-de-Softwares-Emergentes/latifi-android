package com.latifi.wallet.data.api

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.api.dto.toDomain
import com.latifi.wallet.data.api.dto.toSnapshotOrNull
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.rates.ConvertedAmount
import com.latifi.wallet.domain.rates.ExchangeRateRepository

/**
 * `GET /exchange-rate/rates` and `GET /exchange-rate/convert`. A 503 `RATE_UNAVAILABLE` is
 * `RateUnavailable`, and an empty rate list reads the same way. The stale flag is passed through.
 */
class ApiExchangeRateRepository(private val api: LatiFiApi) : ExchangeRateRepository {
    override suspend fun rates(): Result<RateSnapshot> =
        apiResult(call = { api.listRates() }, transform = { it.toSnapshotOrNull() })
            .mapCatching { it ?: throw AppException(AppError.RateUnavailable) }

    override suspend fun convert(amount: TokenAmount, currency: LocalCurrency): Result<ConvertedAmount> =
        apiResult(
            call = { api.convertAmount(amount.baseUnits.toString(), currency.code) },
            transform = { it.toDomain() },
        )
}
