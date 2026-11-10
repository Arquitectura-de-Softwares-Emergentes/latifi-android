package com.latifi.wallet.domain.model

import java.math.BigInteger
import java.time.Instant

/**
 * Reputation level on the 0 to 1000 scale (OpenAPI 0.2.0 `ReputationLevel`).
 * [minScore] is the lower bound of the level.
 */
enum class ReputationLevel(val minScore: Int) {
    NEW(0),
    BUILDING(400),
    TRUSTED(600),
    EXCELLENT(800),
    ;

    companion object {
        /** 0 to 399 is NEW, 400 to 599 BUILDING, 600 to 799 TRUSTED, 800 to 1000 EXCELLENT. A score outside 0 to 1000 is clamped. */
        fun fromScore(score: Int): ReputationLevel {
            val clamped = score.coerceIn(0, 1000)
            return entries.last { clamped >= it.minScore }
        }
    }
}

/** Score and level of the borrower shown on a loan. */
data class BorrowerReputation(val score: Int, val level: ReputationLevel)

/** The reputation screen data (OpenAPI 0.2.0 `ReputationResponse`). */
data class ReputationSummary(
    val score: Int,
    val level: ReputationLevel,
    val onTimeCount: Int,
    val lateCount: Int,
    val defaultCount: Int,
    val profileComplete: Boolean,
    val updatedAt: Instant,
)

enum class ReputationEventType { PROFILE_COMPLETED, REPAID_ON_TIME, REPAID_LATE, DEFAULTED }

/** One row of the reputation history (OpenAPI 0.2.0 `ReputationEvent`). */
data class ReputationEvent(
    val id: Long,
    val loanId: BigInteger?,
    val type: ReputationEventType,
    val delta: Int,
    val scoreAfter: Int,
    val daysLate: Int?,
    val txHash: String?,
    val logIndex: Int?,
    val occurredAt: Instant,
)
