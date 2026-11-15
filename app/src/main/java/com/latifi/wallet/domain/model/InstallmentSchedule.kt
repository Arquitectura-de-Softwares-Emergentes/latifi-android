package com.latifi.wallet.domain.model

import com.latifi.wallet.core.LoanMath
import java.math.BigInteger
import java.time.Instant

enum class InstallmentState { PAID, DUE, UPCOMING }

/** One row of a loan schedule. [index] starts at 1. [dueAt] is null while the loan has no funding time. */
data class InstallmentEntry(
    val index: Int,
    val dueAt: Instant?,
    val amount: BigInteger,
    val state: InstallmentState,
)

/**
 * The schedule of a loan, derived the way the contract derives it: the split of the total due,
 * the due dates from the funding time, and the state of each installment from the amount repaid.
 * Display only. The contract's `nextInstallment` is the authority for what a payment must be.
 */
object InstallmentSchedule {

    /**
     * Rows 1 to N. Installment k is PAID when the amount repaid reaches the sum of the first k
     * amounts. The first unpaid one is DUE (also for a DEFAULTED loan) and the rest are UPCOMING.
     * An OPEN loan owes nothing yet, so all its rows are UPCOMING.
     */
    fun build(loan: Loan): List<InstallmentEntry> {
        val amounts = amounts(loan)
        val firstUnpaid = firstUnpaidIndex(loan, amounts)
        return amounts.mapIndexed { position, amount ->
            val index = position + 1
            InstallmentEntry(
                index = index,
                dueAt = loan.fundedAt?.let { LoanMath.dueAt(it, loan.termDays, index, loan.installments) },
                amount = amount,
                state = when {
                    index < firstUnpaid -> InstallmentState.PAID
                    index == firstUnpaid && loan.status != LoanStatus.OPEN -> InstallmentState.DUE
                    else -> InstallmentState.UPCOMING
                },
            )
        }
    }

    /**
     * What is left on the current installment: its cumulative amount minus the amount repaid.
     * Zero once the loan is fully paid. Display only.
     */
    fun remainingOnCurrent(loan: Loan): BigInteger {
        val amounts = amounts(loan)
        val firstUnpaid = firstUnpaidIndex(loan, amounts)
        if (firstUnpaid > amounts.size) return BigInteger.ZERO
        val cumulative = amounts.take(firstUnpaid).fold(BigInteger.ZERO, BigInteger::add)
        return cumulative.subtract(loan.amountRepaid).max(BigInteger.ZERO)
    }

    /**
     * The total due of an active loan is stored by the contract at funding. Before funding the API
     * reports zero, so the schedule falls back to the same formula the contract will apply.
     */
    private fun amounts(loan: Loan): List<BigInteger> {
        val totalDue = if (loan.totalDue.signum() > 0) {
            loan.totalDue
        } else {
            LoanMath.totalDue(loan.principal, loan.interestRateBps, loan.termDays)
        }
        return LoanMath.installmentAmounts(totalDue, loan.installments)
    }

    /** 1 based index of the first installment not covered by the amount repaid, or N + 1 when all are. */
    private fun firstUnpaidIndex(loan: Loan, amounts: List<BigInteger>): Int {
        var cumulative = BigInteger.ZERO
        for ((position, amount) in amounts.withIndex()) {
            cumulative = cumulative.add(amount)
            if (loan.amountRepaid < cumulative) return position + 1
        }
        return amounts.size + 1
    }
}
