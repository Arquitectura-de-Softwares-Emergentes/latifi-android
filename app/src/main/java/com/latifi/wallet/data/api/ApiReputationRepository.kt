package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.toDomain
import com.latifi.wallet.domain.model.Page
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationSummary
import com.latifi.wallet.domain.reputation.ReputationRepository

/** `GET /reputation/{address}` and its `/history`. A 404 `REPUTATION_NOT_FOUND` is `ReputationNotFound`. */
class ApiReputationRepository(private val api: LatiFiApi) : ReputationRepository {
    override suspend fun get(address: String): Result<ReputationSummary> =
        apiResult(call = { api.getReputation(address.lowercase()) }, transform = { it.toDomain() })

    override suspend fun history(
        address: String,
        type: ReputationEventType?,
        page: Int,
        size: Int,
    ): Result<Page<ReputationEvent>> =
        apiResult(
            call = { api.getReputationHistory(address.lowercase(), type?.name, page, size) },
            transform = { it.toDomain() },
        )
}
