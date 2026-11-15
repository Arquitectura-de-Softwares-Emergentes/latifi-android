package com.latifi.wallet.data.fake

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.Page
import java.math.BigInteger
import java.time.Clock

/**
 * In-memory loans that apply every filter, sort and paging rule of `GET /loans` (OpenAPI 0.2.0),
 * so the real repository of plan 05-13 can replace it without a change above this interface.
 *
 * Sorts: `reputation` by borrower score, highest first. `amount` largest first. `term` shortest
 * first. `rate` highest first. `recent` newest request first. `nextDue` soonest next due date
 * first, loans with none last. Ties break by loan id, highest first. A [LoanQuery.size] outside
 * 1 to 100 or a negative page fails like the API's VALIDATION_FAILED.
 *
 * A test queues a failure with [failNext], reads [queries] to see what the screen asked for, and
 * slows a call down with [beforeList] to prove that a late answer never wins.
 */
class FakeLoanRepository(
    var loans: List<Loan>,
) : LoanRepository {
    constructor(clock: Clock = Clock.systemUTC()) : this(FakeData.loans(clock))

    /** The next call to [list] or [get] fails with this error, once. */
    var failNext: AppError? = null

    /** Runs before [list] answers. A test suspends here to hold an old query back. */
    var beforeList: suspend (LoanQuery) -> Unit = {}

    /** Every query [list] received, in order. */
    val queries = mutableListOf<LoanQuery>()

    override suspend fun list(query: LoanQuery): Result<Page<Loan>> {
        queries += query
        beforeList(query)
        takeFailure()?.let { return Result.failure(AppException(it)) }
        if (query.page < 0) return Result.failure(AppException(AppError.Validation("page")))
        if (query.size !in 1..LoanQuery.MAX_SIZE) return Result.failure(AppException(AppError.Validation("size")))

        val matching = loans.filter { matches(it, query) }.sortedWith(comparator(query.sort))
        val from = query.page.toLong() * query.size.toLong()
        val items = if (from >= matching.size) emptyList() else matching.subList(from.toInt(), minOf(matching.size, (from + query.size).toInt()))
        return Result.success(Page(items, query.page, query.size, matching.size.toLong()))
    }

    override suspend fun get(loanId: BigInteger): Result<Loan> {
        takeFailure()?.let { return Result.failure(AppException(it)) }
        val loan = loans.firstOrNull { it.loanId == loanId } ?: return Result.failure(AppException(AppError.NotFound))
        return Result.success(loan)
    }

    private fun matches(loan: Loan, query: LoanQuery): Boolean =
        (query.status == null || loan.status == query.status) &&
            (query.minAmount == null || loan.principal >= query.minAmount) &&
            (query.maxAmount == null || loan.principal <= query.maxAmount) &&
            (query.maxTermDays == null || loan.termDays <= query.maxTermDays) &&
            (query.minLevel == null || loan.borrowerReputation.level >= query.minLevel) &&
            (query.borrower == null || loan.borrower.equals(query.borrower, ignoreCase = true)) &&
            (query.lender == null || loan.lender.equals(query.lender, ignoreCase = true))

    private fun comparator(sort: LoanSort): Comparator<Loan> {
        val byIdDesc = compareByDescending<Loan> { it.loanId }
        val main: Comparator<Loan> = when (sort) {
            LoanSort.Reputation -> compareByDescending { it.borrowerReputation.score }
            LoanSort.Amount -> compareByDescending { it.principal }
            LoanSort.Term -> compareBy { it.termDays }
            LoanSort.Rate -> compareByDescending { it.interestRateBps }
            LoanSort.Recent -> compareByDescending { it.requestedAt }
            LoanSort.NextDue -> Comparator { a, b ->
                val left = a.nextDueDate
                val right = b.nextDueDate
                when {
                    left == null && right == null -> 0
                    left == null -> 1
                    right == null -> -1
                    else -> left.compareTo(right)
                }
            }
        }
        return main.then(byIdDesc)
    }

    private fun takeFailure(): AppError? = failNext.also { failNext = null }
}
