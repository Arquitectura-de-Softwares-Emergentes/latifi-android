package com.latifi.wallet.data.api.dto

import com.latifi.wallet.domain.auth.Challenge
import com.latifi.wallet.domain.auth.Token
import com.latifi.wallet.domain.model.BorrowerReputation
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.Page
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.model.ReputationSummary
import com.latifi.wallet.domain.rates.ConvertedAmount
import kotlinx.serialization.SerializationException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/*
 * DTO to domain. A value the contract forbids (a malformed digit string, a bad timestamp) throws
 * SerializationException, which the call helper turns into AppError.Server. An enum value the app
 * does not know is not an error: an unknown loan status or event type skips the item, and an
 * unknown reputation level reads as NEW.
 */

private val UINT = Regex("^[0-9]{1,78}$")
private val DECIMAL = Regex("^[0-9]+(\\.[0-9]+)?$")

/** A `Uint256` digit string as a [BigInteger]. A 78 digit value survives. */
internal fun String.toUint256(): BigInteger {
    if (!UINT.matches(this)) throw SerializationException("A uint256 field is not a digit string")
    return BigInteger(this)
}

/** A `DecimalString` as a [BigDecimal], never through a binary number. */
internal fun String.toDecimalValue(): BigDecimal {
    if (!DECIMAL.matches(this)) throw SerializationException("A decimal field is not a decimal string")
    return BigDecimal(this)
}

/** RFC 3339 date-time. `OffsetDateTime` reads both `Z` and numeric offsets on every API level. */
internal fun String.toInstantValue(): Instant =
    try {
        OffsetDateTime.parse(this).toInstant()
    } catch (e: DateTimeParseException) {
        throw SerializationException("A timestamp field is not an RFC 3339 date-time", e)
    }

internal fun String.toAddress(): String = lowercase()

internal fun String.toLoanStatusOrNull(): LoanStatus? = LoanStatus.entries.firstOrNull { it.name == this }

internal fun String.toLevel(): ReputationLevel = ReputationLevel.entries.firstOrNull { it.name == this } ?: ReputationLevel.NEW

internal fun String.toEventTypeOrNull(): ReputationEventType? =
    ReputationEventType.entries.firstOrNull { it.name == this }

private fun String.toContactTypeOrNull(): ContactType? = ContactType.entries.firstOrNull { it.name == this }

/** Null when the status is one the app does not know, so a page never fails for a new status. */
fun LoanSummaryDto.toDomainOrNull(): Loan? {
    val loanStatus = status.toLoanStatusOrNull() ?: return null
    return Loan(
        loanId = loanId.toUint256(),
        borrower = borrower.toAddress(),
        lender = lender?.toAddress(),
        principal = principal.toUint256(),
        interestRateBps = interestRateBps,
        termDays = termDays,
        installments = installments,
        status = loanStatus,
        amountRepaid = amountRepaid.toUint256(),
        totalDue = totalDue.toUint256(),
        installmentsPaid = installmentsPaid,
        requestedAt = requestedAt.toInstantValue(),
        fundedAt = fundedAt?.toInstantValue(),
        finalDueDate = finalDueDate?.toInstantValue(),
        nextDueDate = nextDueDate?.toInstantValue(),
        borrowerReputation = BorrowerReputation(
            score = borrowerReputation.score.coerceIn(SCORE_MIN, SCORE_MAX),
            level = borrowerReputation.level.toLevel(),
        ),
    )
}

/** Items with an unknown status are skipped. [Page.totalItems] stays what the server counted. */
fun LoanPageDto.toDomain(): Page<Loan> =
    Page(items = items.mapNotNull { it.toDomainOrNull() }, page = page, size = size, totalItems = totalItems)

fun ChallengeResponseDto.toDomain(): Challenge =
    Challenge(
        challengeId = challengeId,
        nonce = nonce,
        // The wallet signs this text exactly as the server wrote it.
        message = message,
        issuedAt = issuedAt.toInstantValue(),
        expiresAt = expiresAt.toInstantValue(),
    )

fun TokenResponseDto.toDomain(): Token =
    Token(accessToken = accessToken, expiresInSeconds = expiresIn, address = address.toAddress())

fun ProfileResponseDto.toDomain(): Profile =
    Profile(
        address = address.toAddress(),
        displayName = displayName,
        contact = contact?.let { dto -> dto.type.toContactTypeOrNull()?.let { Contact(it, dto.value) } },
        createdAt = createdAt.toInstantValue(),
        updatedAt = updatedAt.toInstantValue(),
    )

fun Contact.toDto(): ContactDto = ContactDto(type = type.name, value = value)

fun ReputationResponseDto.toDomain(): ReputationSummary =
    ReputationSummary(
        score = score.coerceIn(SCORE_MIN, SCORE_MAX),
        level = level.toLevel(),
        onTimeCount = onTimeCount,
        lateCount = lateCount,
        defaultCount = defaultCount,
        profileComplete = profileComplete,
        updatedAt = updatedAt.toInstantValue(),
    )

/** Null for an event type the app does not know, so the history skips it. */
fun ReputationEventDto.toDomainOrNull(): ReputationEvent? {
    val eventType = type.toEventTypeOrNull() ?: return null
    return ReputationEvent(
        id = id,
        loanId = loanId?.toUint256(),
        type = eventType,
        delta = delta,
        scoreAfter = scoreAfter,
        daysLate = daysLate,
        txHash = txHash,
        logIndex = logIndex,
        occurredAt = occurredAt.toInstantValue(),
    )
}

fun ReputationHistoryPageDto.toDomain(): Page<ReputationEvent> =
    Page(items = items.mapNotNull { it.toDomainOrNull() }, page = page, size = size, totalItems = totalItems)

fun ConvertResponseDto.toDomain(): ConvertedAmount =
    ConvertedAmount(
        value = value.toDecimalValue(),
        rate = rate.toDecimalValue(),
        asOf = asOf.toInstantValue(),
        stale = stale,
    )

fun RateQuoteDto.toDomain(): RateQuote =
    RateQuote(base = base, quote = quote, rate = rate.toDecimalValue(), asOf = asOf.toInstantValue(), stale = stale)

/** The snapshot takes the oldest quote time and is stale when any quote is. Null for an empty list. */
fun List<RateQuoteDto>.toSnapshotOrNull(): RateSnapshot? {
    val quotes = map { it.toDomain() }
    val oldest = quotes.minOfOrNull { it.asOf } ?: return null
    return RateSnapshot(quotes = quotes, asOf = oldest, stale = quotes.any { it.stale })
}

private const val SCORE_MIN = 0
private const val SCORE_MAX = 1000
