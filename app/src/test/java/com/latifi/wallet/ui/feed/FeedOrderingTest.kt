package com.latifi.wallet.ui.feed

import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.domain.feed.FeedFilters
import com.latifi.wallet.domain.feed.FeedQueryMapper
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * REP-03 against the fake repository, whose rules mirror `GET /loans`: the default order is the
 * highest borrower reputation first, and every feed query only returns open requests.
 */
class FeedOrderingTest {
    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val repo = FakeLoanRepository(clock)

    private suspend fun first(filters: FeedFilters = FeedFilters()): List<Loan> =
        repo.list(FeedQueryMapper.toQuery(filters)).getOrThrow().items

    @Test
    fun theDefaultFirstPageIsOrderedByBorrowerScoreFromHighestToLowest() = runTest {
        val scores = first().map { it.borrowerReputation.score }

        assertEquals(scores.sortedDescending(), scores)
        assertEquals(listOf(1L, 2L, 12L, 10L, 4L, 6L), first().map { it.loanId.toLong() })
    }

    @Test
    fun theCounterCountsEveryOpenLoanAndNothingElse() = runTest {
        val page = repo.list(FeedQueryMapper.toQuery(FeedFilters())).getOrThrow()
        val open = FakeData.loans(clock).count { it.status == LoanStatus.OPEN }

        assertEquals(open.toLong(), page.totalItems)
        assertTrue(page.items.all { it.status == LoanStatus.OPEN })
    }
}
