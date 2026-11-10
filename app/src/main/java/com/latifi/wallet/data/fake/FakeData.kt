package com.latifi.wallet.data.fake

import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.BorrowerReputation
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import java.math.BigInteger
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The seed of the demo build and of the repository tests. Everything is relative to the clock,
 * so the demo always shows a loan with a next due date in the future. Addresses are public test
 * vectors in lowercase, as the API reports them. The demo wallet is the borrower of loans in
 * every status and the lender of two loans of other people.
 */
object FakeData {
    /** The demo wallet of [FakeWalletGateway], in the lowercase form of the API. */
    val DEMO_ADDRESS: String = FakeWalletGateway.DEMO_ADDRESS.lowercase()

    // Borrowers of other people. The first three have public names in FakeProfileRepository.
    const val LUCIA = "0x1111111111111111111111111111111111111111"
    const val MATEO = "0x2222222222222222222222222222222222222222"
    const val CAMILA = "0x3333333333333333333333333333333333333333"
    const val NEW_BORROWER = "0x4444444444444444444444444444444444444444"

    // Lenders of other people. They have no public name, so the screens show a short address.
    const val LENDER_A = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val LENDER_B = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    /** One loan. [paid] installments are covered by [Loan.amountRepaid]. Days are counted back from now. */
    @Suppress("LongParameterList")
    fun loan(
        clock: Clock,
        id: Long,
        borrower: String,
        lender: String?,
        tokens: Long,
        bps: Int,
        termDays: Int,
        installments: Int,
        status: LoanStatus,
        paid: Int = 0,
        requestedDaysAgo: Long,
        fundedDaysAgo: Long? = null,
        score: Int = 620,
    ): Loan {
        val now = clock.instant()
        val principal = TokenAmount.ofTokens(tokens).baseUnits
        val funded = fundedDaysAgo?.let { now.minus(Duration.ofDays(it)) }
        val totalDue = if (funded != null) LoanMath.totalDue(principal, bps, termDays) else BigInteger.ZERO
        val amounts = if (funded != null) LoanMath.installmentAmounts(totalDue, installments) else emptyList()
        val repaid = amounts.take(paid).fold(BigInteger.ZERO, BigInteger::add)
        val nextIndex = paid + 1
        val nextDue = if (funded != null && status != LoanStatus.REPAID && nextIndex <= installments) {
            LoanMath.dueAt(funded, termDays, nextIndex, installments)
        } else {
            null
        }
        return Loan(
            loanId = BigInteger.valueOf(id),
            borrower = borrower,
            lender = lender,
            principal = principal,
            interestRateBps = bps,
            termDays = termDays,
            installments = installments,
            status = status,
            amountRepaid = repaid,
            totalDue = totalDue,
            installmentsPaid = paid,
            requestedAt = now.minus(Duration.ofDays(requestedDaysAgo)),
            fundedAt = funded,
            finalDueDate = funded?.plus(Duration.ofDays(termDays.toLong())),
            nextDueDate = nextDue,
            borrowerReputation = BorrowerReputation(score, ReputationLevel.fromScore(score)),
        )
    }

    /** Fifteen loans. Ids grow with the request time, so `recent` is the descending id order. */
    fun loans(clock: Clock): List<Loan> = listOf(
        // The demo wallet as borrower, one loan per status plus an older paid one.
        loan(clock, 3, DEMO_ADDRESS, LENDER_B, 60, 900, 30, 1, LoanStatus.REPAID, paid = 1, requestedDaysAgo = 150, fundedDaysAgo = 148),
        loan(clock, 5, DEMO_ADDRESS, LENDER_A, 400, 1500, 30, 2, LoanStatus.DEFAULTED, paid = 0, requestedDaysAgo = 52, fundedDaysAgo = 50),
        loan(clock, 7, DEMO_ADDRESS, LENDER_B, 100, 1200, 60, 4, LoanStatus.REPAID, paid = 4, requestedDaysAgo = 92, fundedDaysAgo = 90),
        loan(clock, 11, DEMO_ADDRESS, LENDER_A, 250, 1000, 30, 3, LoanStatus.ACTIVE, paid = 1, requestedDaysAgo = 13, fundedDaysAgo = 12),
        loan(clock, 12, DEMO_ADDRESS, null, 120, 800, 45, 2, LoanStatus.OPEN, requestedDaysAgo = 1),
        // The demo wallet as lender.
        loan(clock, 8, LUCIA, DEMO_ADDRESS, 300, 1100, 60, 4, LoanStatus.ACTIVE, paid = 2, requestedDaysAgo = 40, fundedDaysAgo = 38, score = 820),
        loan(clock, 9, MATEO, DEMO_ADDRESS, 150, 1000, 45, 3, LoanStatus.REPAID, paid = 3, requestedDaysAgo = 80, fundedDaysAgo = 78, score = 650),
        // Open requests of other people at every reputation level.
        loan(clock, 1, LUCIA, null, 500, 700, 60, 6, LoanStatus.OPEN, requestedDaysAgo = 6, score = 820),
        loan(clock, 2, MATEO, null, 200, 1200, 30, 2, LoanStatus.OPEN, requestedDaysAgo = 5, score = 650),
        loan(clock, 4, CAMILA, null, 80, 1500, 45, 1, LoanStatus.OPEN, requestedDaysAgo = 4, score = 450),
        loan(clock, 6, NEW_BORROWER, null, 50, 2000, 30, 1, LoanStatus.OPEN, requestedDaysAgo = 3, score = 120),
        loan(clock, 10, CAMILA, null, 350, 900, 60, 5, LoanStatus.OPEN, requestedDaysAgo = 2, score = 450),
        // Funded and running loans of other people.
        loan(clock, 13, MATEO, LENDER_A, 180, 1000, 30, 2, LoanStatus.ACTIVE, paid = 1, requestedDaysAgo = 20, fundedDaysAgo = 19, score = 650),
        loan(clock, 14, NEW_BORROWER, LENDER_B, 70, 1800, 45, 3, LoanStatus.DEFAULTED, paid = 1, requestedDaysAgo = 70, fundedDaysAgo = 68, score = 120),
        loan(clock, 15, LUCIA, LENDER_A, 450, 800, 45, 3, LoanStatus.REPAID, paid = 3, requestedDaysAgo = 110, fundedDaysAgo = 108, score = 820),
    )

    /** A fixed "now" for tests that build the seed with a fixed clock. */
    val EPOCH: Instant = Instant.parse("2026-11-10T12:00:00Z")
}
