package com.latifi.wallet.data.fake

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.ZoneOffset

class FakeLoanRepositoryTest {
    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val repo = FakeLoanRepository(clock)

    private suspend fun ids(query: LoanQuery): List<Long> =
        repo.list(query).getOrThrow().items.map { it.loanId.toLong() }

    private suspend fun idSet(query: LoanQuery): Set<Long> = ids(query.copy(size = 100)).toSet()

    @Test
    fun theSeedHasEveryStatusForTheDemoBorrowerAndTwoLoansAsLender() = runTest {
        val own = repo.list(LoanQuery(borrower = FakeData.DEMO_ADDRESS, size = 100)).getOrThrow().items
        assertEquals(LoanStatus.entries.toSet(), own.map { it.status }.toSet())
        assertEquals(setOf(8L, 9L), idSet(LoanQuery(lender = FakeData.DEMO_ADDRESS)))
        val others = repo.list(LoanQuery(size = 100)).getOrThrow().items.filter { it.borrower != FakeData.DEMO_ADDRESS }
        assertEquals(ReputationLevel.entries.toSet(), others.map { it.borrowerReputation.level }.toSet())
    }

    @Test
    fun theSeedKeepsAnActiveLoanWithOneOfThreeInstallmentsPaid() = runTest {
        val loan = repo.get(BigInteger.valueOf(11)).getOrThrow()
        assertEquals(LoanStatus.ACTIVE, loan.status)
        assertEquals(3, loan.installments)
        assertEquals(1, loan.installmentsPaid)
        assertEquals(TokenAmount.ofTokens(250).baseUnits, loan.principal)
        assertTrue(loan.nextDueDate!!.isAfter(FakeData.EPOCH))
    }

    @Test
    fun eachFilterAlone() = runTest {
        assertEquals(setOf(3L, 7L, 9L, 15L), idSet(LoanQuery(status = LoanStatus.REPAID)))
        assertEquals(setOf(5L, 14L), idSet(LoanQuery(status = LoanStatus.DEFAULTED)))
        assertEquals(setOf(3L, 5L, 7L, 11L, 12L), idSet(LoanQuery(borrower = FakeData.DEMO_ADDRESS)))
        assertEquals(setOf(3L, 5L, 7L, 11L, 12L), idSet(LoanQuery(borrower = FakeData.DEMO_ADDRESS.uppercase().replace("0X", "0x"))))
        assertEquals(setOf(8L, 9L), idSet(LoanQuery(lender = FakeData.DEMO_ADDRESS)))
        assertEquals(setOf(1L, 5L, 8L, 10L, 15L), idSet(LoanQuery(minAmount = TokenAmount.ofTokens(300).baseUnits)))
        assertEquals(setOf(3L, 4L, 6L, 7L, 14L), idSet(LoanQuery(maxAmount = TokenAmount.ofTokens(100).baseUnits)))
        assertEquals(setOf(2L, 3L, 5L, 6L, 11L, 13L), idSet(LoanQuery(maxTermDays = 30)))
        assertEquals(setOf(1L, 2L, 3L, 5L, 7L, 8L, 9L, 11L, 12L, 13L, 15L), idSet(LoanQuery(minLevel = ReputationLevel.TRUSTED)))
    }

    @Test
    fun filtersCombine() = runTest {
        assertEquals(setOf(11L), idSet(LoanQuery(borrower = FakeData.DEMO_ADDRESS, status = LoanStatus.ACTIVE)))
        assertEquals(setOf(1L), idSet(LoanQuery(status = LoanStatus.OPEN, minLevel = ReputationLevel.EXCELLENT)))
        assertEquals(
            setOf(2L),
            idSet(LoanQuery(status = LoanStatus.OPEN, maxTermDays = 30, minAmount = TokenAmount.ofTokens(150).baseUnits, minLevel = ReputationLevel.TRUSTED)),
        )
        assertEquals(emptySet<Long>(), idSet(LoanQuery(status = LoanStatus.REPAID, maxAmount = TokenAmount.ofTokens(10).baseUnits)))
    }

    @Test
    fun recentSortsNewestRequestFirst() = runTest {
        assertEquals(
            listOf(12L, 10L, 6L, 4L, 2L, 1L, 11L, 13L, 8L, 5L, 14L, 9L, 7L, 15L, 3L),
            ids(LoanQuery(sort = LoanSort.Recent, size = 100)),
        )
    }

    @Test
    fun nextDueSortsSoonestFirstAndLoansWithNoDueDateLast() = runTest {
        assertEquals(
            listOf(14L, 5L, 8L, 11L, 13L, 15L, 12L, 10L, 9L, 7L, 6L, 4L, 3L, 2L, 1L),
            ids(LoanQuery(sort = LoanSort.NextDue, size = 100)),
        )
    }

    @Test
    fun reputationAmountTermAndRateSortInTheDocumentedDirection() = runTest {
        fun List<Loan>.assertOrdered(key: (Loan) -> Comparable<*>, descending: Boolean) {
            zipWithNext().forEach { (a, b) ->
                @Suppress("UNCHECKED_CAST")
                val order = (key(a) as Comparable<Any>).compareTo(key(b))
                if (order == 0) {
                    assertTrue("ties break by loan id, highest first", a.loanId > b.loanId)
                } else {
                    assertTrue("${a.loanId} before ${b.loanId}", if (descending) order > 0 else order < 0)
                }
            }
        }
        suspend fun items(sort: LoanSort) = repo.list(LoanQuery(sort = sort, size = 100)).getOrThrow().items

        items(LoanSort.Reputation).assertOrdered({ it.borrowerReputation.score }, descending = true)
        items(LoanSort.Amount).assertOrdered({ it.principal }, descending = true)
        items(LoanSort.Term).assertOrdered({ it.termDays }, descending = false)
        items(LoanSort.Rate).assertOrdered({ it.interestRateBps }, descending = true)
        assertEquals(listOf(6L, 14L), ids(LoanQuery(sort = LoanSort.Rate, size = 2)))
        assertEquals(1L, ids(LoanQuery(sort = LoanSort.Amount, size = 1)).single())
    }

    @Test
    fun pagesSliceTheSortedListAndTotalItemsIgnoresTheSize() = runTest {
        val first = repo.list(LoanQuery(sort = LoanSort.Recent, size = 5)).getOrThrow()
        val second = repo.list(LoanQuery(sort = LoanSort.Recent, page = 1, size = 5)).getOrThrow()
        val everything = repo.list(LoanQuery(sort = LoanSort.Recent, size = 100)).getOrThrow()

        assertEquals(listOf(12L, 10L, 6L, 4L, 2L), first.items.map { it.loanId.toLong() })
        assertEquals(listOf(1L, 11L, 13L, 8L, 5L), second.items.map { it.loanId.toLong() })
        assertEquals(15L, first.totalItems)
        assertEquals(15L, second.totalItems)
        assertEquals(15L, everything.totalItems)
        assertTrue(first.hasMore)
        assertFalse(everything.hasMore)
        assertEquals(1, second.page)
        assertEquals(5, second.size)
    }

    @Test
    fun aPageBeyondTheEndIsEmptyWithTheRealTotal() = runTest {
        val page = repo.list(LoanQuery(page = 9, size = 5)).getOrThrow()
        assertTrue(page.items.isEmpty())
        assertEquals(15L, page.totalItems)
    }

    @Test
    fun theSizeAndPageBoundsFailLikeValidationFailed() = runTest {
        for (query in listOf(LoanQuery(size = 0), LoanQuery(size = 101), LoanQuery(page = -1))) {
            val error = (repo.list(query).exceptionOrNull() as AppException).error
            assertTrue(error.toString(), error is AppError.Validation)
        }
        assertEquals(100, repo.list(LoanQuery(size = 100)).getOrThrow().size)
        assertEquals(1, repo.list(LoanQuery(size = 1)).getOrThrow().items.size)
    }

    @Test
    fun getFindsALoanAndAnUnknownIdIsNotFound() = runTest {
        assertEquals(BigInteger.valueOf(7), repo.get(BigInteger.valueOf(7)).getOrThrow().loanId)
        assertEquals(AppError.NotFound, (repo.get(BigInteger.valueOf(999)).exceptionOrNull() as AppException).error)
    }

    @Test
    fun aQueuedFailureHitsOnlyTheNextCall() = runTest {
        repo.failNext = AppError.Offline
        assertEquals(AppError.Offline, (repo.list(LoanQuery()).exceptionOrNull() as AppException).error)
        assertTrue(repo.list(LoanQuery()).isSuccess)
        assertEquals(2, repo.queries.size)
    }
}
