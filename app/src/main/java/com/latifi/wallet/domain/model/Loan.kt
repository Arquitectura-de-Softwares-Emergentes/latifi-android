package com.latifi.wallet.domain.model

import java.math.BigInteger
import java.time.Instant

/** Loan status as the API reports it (OpenAPI 0.2.0 `LoanStatus`). */
enum class LoanStatus { OPEN, ACTIVE, REPAID, DEFAULTED }

/** Status as the person reads it. Language follows the UI-SPEC chips. */
enum class UiLoanStatus { Open, Pending, Paid, Overdue }

/** OPEN to Open, ACTIVE to Pending, REPAID to Paid, DEFAULTED to Overdue. */
fun LoanStatus.toUi(): UiLoanStatus = when (this) {
    LoanStatus.OPEN -> UiLoanStatus.Open
    LoanStatus.ACTIVE -> UiLoanStatus.Pending
    LoanStatus.REPAID -> UiLoanStatus.Paid
    LoanStatus.DEFAULTED -> UiLoanStatus.Overdue
}

/**
 * A loan as the API lists it (OpenAPI 0.2.0 `LoanSummary`). Amounts are base units of the token
 * with 6 decimals. [borrower] and [lender] are lowercase wallet addresses, normalized by the
 * mapper that builds this object. [lender], [fundedAt], [finalDueDate] and [nextDueDate] stay
 * null until the loan is funded (and [nextDueDate] again once nothing is pending).
 */
data class Loan(
    val loanId: BigInteger,
    val borrower: String,
    val lender: String?,
    val principal: BigInteger,
    val interestRateBps: Int,
    val termDays: Int,
    val installments: Int,
    val status: LoanStatus,
    val amountRepaid: BigInteger,
    val totalDue: BigInteger,
    val installmentsPaid: Int,
    val requestedAt: Instant,
    val fundedAt: Instant?,
    val finalDueDate: Instant?,
    val nextDueDate: Instant?,
    val borrowerReputation: BorrowerReputation,
) {
    val uiStatus: UiLoanStatus get() = status.toUi()
}
