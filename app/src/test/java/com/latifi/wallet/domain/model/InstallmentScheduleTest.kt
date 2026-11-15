package com.latifi.wallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger
import java.time.Instant

class InstallmentScheduleTest {
    private val fundedAt: Instant = Instant.ofEpochSecond(1_800_000_000L)

    private fun big(value: Long): BigInteger = BigInteger.valueOf(value)

    /** 1000 tokens, 1000 bps, 90 days, 3 installments: total 1,024,657,534 (LOANBOOK.md example). */
    private fun loan(
        repaid: Long = 0,
        status: LoanStatus = LoanStatus.ACTIVE,
        funded: Instant? = fundedAt,
        totalDue: Long = 1_024_657_534L,
    ): Loan = Loan(
        loanId = big(7),
        borrower = "0x1111111111111111111111111111111111111111",
        lender = if (funded == null) null else "0x2222222222222222222222222222222222222222",
        principal = big(1_000_000_000L),
        interestRateBps = 1000,
        termDays = 90,
        installments = 3,
        status = status,
        amountRepaid = big(repaid),
        totalDue = big(totalDue),
        installmentsPaid = 0,
        requestedAt = Instant.ofEpochSecond(1_799_000_000L),
        fundedAt = funded,
        finalDueDate = null,
        nextDueDate = null,
        borrowerReputation = BorrowerReputation(620, ReputationLevel.TRUSTED),
    )

    private fun states(loan: Loan): List<InstallmentState> = InstallmentSchedule.build(loan).map { it.state }

    @Test
    fun theAmountsFollowTheContractSplit() {
        val entries = InstallmentSchedule.build(loan())
        assertEquals(listOf(1, 2, 3), entries.map { it.index })
        assertEquals(
            listOf(big(341_552_511L), big(341_552_511L), big(341_552_512L)),
            entries.map { it.amount },
        )
    }

    @Test
    fun theDueDatesStartFromTheFundingTime() {
        val entries = InstallmentSchedule.build(loan())
        // 90 days * 86400 / 3 = 2,592,000 seconds between installments.
        assertEquals(fundedAt.plusSeconds(2_592_000L), entries[0].dueAt)
        assertEquals(fundedAt.plusSeconds(5_184_000L), entries[1].dueAt)
        assertEquals(fundedAt.plusSeconds(7_776_000L), entries[2].dueAt)
    }

    @Test
    fun nothingPaidLeavesTheFirstDue() {
        assertEquals(
            listOf(InstallmentState.DUE, InstallmentState.UPCOMING, InstallmentState.UPCOMING),
            states(loan(repaid = 0)),
        )
    }

    @Test
    fun exactlyOneInstallmentPaidMovesDueToTheSecond() {
        assertEquals(
            listOf(InstallmentState.PAID, InstallmentState.DUE, InstallmentState.UPCOMING),
            states(loan(repaid = 341_552_511L)),
        )
    }

    @Test
    fun aPartialPaymentInsideTheSecondInstallmentKeepsItDue() {
        val partial = loan(repaid = 341_552_511L + 100_000_000L)
        assertEquals(
            listOf(InstallmentState.PAID, InstallmentState.DUE, InstallmentState.UPCOMING),
            states(partial),
        )
        // 683,105,022 - 441,552,511 = 241,552,511 left on installment 2.
        assertEquals(big(241_552_511L), InstallmentSchedule.remainingOnCurrent(partial))
    }

    @Test
    fun aPartialPaymentOneUnitShortKeepsTheInstallmentDue() {
        val almost = loan(repaid = 341_552_510L)
        assertEquals(InstallmentState.DUE, InstallmentSchedule.build(almost)[0].state)
        assertEquals(big(1), InstallmentSchedule.remainingOnCurrent(almost))
    }

    @Test
    fun aFullyRepaidLoanHasEveryInstallmentPaid() {
        val repaid = loan(repaid = 1_024_657_534L, status = LoanStatus.REPAID)
        assertEquals(List(3) { InstallmentState.PAID }, states(repaid))
        assertEquals(BigInteger.ZERO, InstallmentSchedule.remainingOnCurrent(repaid))
    }

    @Test
    fun remainingOnCurrentOfAFreshLoanIsTheFirstInstallment() {
        assertEquals(big(341_552_511L), InstallmentSchedule.remainingOnCurrent(loan()))
    }

    @Test
    fun anOpenLoanHasNoDueDatesAndOwesNothingYet() {
        val open = loan(status = LoanStatus.OPEN, funded = null, totalDue = 0)
        val entries = InstallmentSchedule.build(open)
        assertEquals(3, entries.size)
        assertEquals(List(3) { null }, entries.map { it.dueAt })
        assertNull(entries.first().dueAt)
        assertEquals(List(3) { InstallmentState.UPCOMING }, entries.map { it.state })
        // The total due is not stored before funding, so the schedule uses the contract formula.
        assertEquals(
            listOf(big(341_552_511L), big(341_552_511L), big(341_552_512L)),
            entries.map { it.amount },
        )
    }

    @Test
    fun aDefaultedLoanKeepsTheFirstUnpaidInstallmentDue() {
        val defaulted = loan(repaid = 341_552_511L, status = LoanStatus.DEFAULTED)
        assertEquals(
            listOf(InstallmentState.PAID, InstallmentState.DUE, InstallmentState.UPCOMING),
            states(defaulted),
        )
    }

    @Test
    fun aTinyTotalPutsEverythingOnTheLastInstallment() {
        val dust = loan(totalDue = 1).copy(installments = 6, principal = big(1), interestRateBps = 1, termDays = 1)
        val entries = InstallmentSchedule.build(dust)
        assertEquals(List(5) { BigInteger.ZERO } + BigInteger.ONE, entries.map { it.amount })
        assertEquals(InstallmentState.DUE, entries.last().state)
    }
}
