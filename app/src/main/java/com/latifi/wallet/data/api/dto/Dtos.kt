package com.latifi.wallet.data.api.dto

import kotlinx.serialization.Serializable

/*
 * Wire shapes of OpenAPI 0.2.0, field for field. Three rules keep them honest:
 * - `Uint256` and `DecimalString` fields are String, so no digit is lost to a number type.
 * - Enum fields are String too. The mappers decide what an unknown value means, so a new server
 *   value never breaks the parsing of a whole page.
 * - Nullable contract fields are nullable here, and a missing key reads as null.
 */

@Serializable
data class ApiErrorDto(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class ChallengeRequestDto(val address: String)

@Serializable
data class ChallengeResponseDto(
    val challengeId: String,
    val nonce: String,
    val message: String,
    val issuedAt: String,
    val expiresAt: String,
)

@Serializable
data class VerifyRequestDto(
    val challengeId: String,
    val address: String,
    val signature: String,
)

@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val address: String,
)

@Serializable
data class ContactDto(val type: String, val value: String)

@Serializable
data class ProfileUpdateRequestDto(val displayName: String, val contact: ContactDto)

@Serializable
data class ProfileResponseDto(
    val address: String,
    val displayName: String,
    val contact: ContactDto? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ReputationResponseDto(
    val address: String,
    val score: Int,
    val level: String,
    val onTimeCount: Int,
    val lateCount: Int,
    val defaultCount: Int,
    val profileComplete: Boolean,
    val updatedAt: String,
)

@Serializable
data class ReputationEventDto(
    val id: Long,
    val loanId: String? = null,
    val type: String,
    val delta: Int,
    val scoreAfter: Int,
    val daysLate: Int? = null,
    val txHash: String? = null,
    val logIndex: Int? = null,
    val occurredAt: String,
)

@Serializable
data class ReputationHistoryPageDto(
    val items: List<ReputationEventDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
)

@Serializable
data class BorrowerReputationDto(val score: Int, val level: String)

@Serializable
data class LoanSummaryDto(
    val loanId: String,
    val borrower: String,
    val lender: String? = null,
    val principal: String,
    val interestRateBps: Int,
    val termDays: Int,
    val installments: Int,
    val status: String,
    val amountRepaid: String,
    val totalDue: String,
    val installmentsPaid: Int,
    val requestedAt: String,
    val fundedAt: String? = null,
    val finalDueDate: String? = null,
    val nextDueDate: String? = null,
    val borrowerReputation: BorrowerReputationDto,
)

@Serializable
data class LoanPageDto(
    val items: List<LoanSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
)

@Serializable
data class ConvertResponseDto(
    val amount: String,
    val currency: String,
    val value: String,
    val rate: String,
    val asOf: String,
    val stale: Boolean,
)

@Serializable
data class RateQuoteDto(
    val base: String,
    val quote: String,
    val rate: String,
    val asOf: String,
    val stale: Boolean,
)
