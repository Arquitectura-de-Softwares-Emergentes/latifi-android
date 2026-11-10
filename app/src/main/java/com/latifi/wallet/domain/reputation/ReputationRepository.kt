package com.latifi.wallet.domain.reputation

import com.latifi.wallet.domain.model.Page
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationSummary

/**
 * The reputation reads of the API (`GET /reputation/{address}` and its `/history`). Failures
 * travel in `Result` as `AppException`: an address with no profile fails with
 * `AppError.ReputationNotFound` (404 REPUTATION_NOT_FOUND), so a screen can offer to add details.
 */
interface ReputationRepository {
    /** The score, the level, the counters and the time of the last update for [address]. */
    suspend fun get(address: String): Result<ReputationSummary>

    /**
     * One page of the history, newest first. [type] filters exactly. `PROFILE_COMPLETED` is only
     * returned when [type] is null, because no filter chip asks for it.
     */
    suspend fun history(
        address: String,
        type: ReputationEventType?,
        page: Int,
        size: Int,
    ): Result<Page<ReputationEvent>>
}
