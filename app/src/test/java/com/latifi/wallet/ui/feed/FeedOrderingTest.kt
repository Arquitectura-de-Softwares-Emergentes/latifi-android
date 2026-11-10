package com.latifi.wallet.ui.feed

import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.domain.feed.AmountRange
import com.latifi.wallet.domain.feed.FeedFilters
import com.latifi.wallet.domain.feed.FeedQueryMapper
import com.latifi.wallet.domain.feed.FeedSort
import com.latifi.wallet.domain.feed.Risk
import com.latifi.wallet.domain.feed.TermLimit
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.core.TokenAmount
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

    @Test
    fun lowestRiskReturnsOnlyExcellentBorrowers() = runTest {
        val levels = first(FeedFilters(risk = Risk.Lowest)).map { it.borrowerReputation.level }.toSet()

        assertEquals(setOf(ReputationLevel.EXCELLENT), levels)
    }

    @Test
    fun lowRiskReturnsTrustedAndExcellentOnly() = runTest {
        val levels = first(FeedFilters(risk = Risk.Low)).map { it.borrowerReputation.level }.toSet()

        assertEquals(setOf(ReputationLevel.EXCELLENT, ReputationLevel.TRUSTED), levels)
    }

    @Test
    fun mediumRiskLeavesOutOnlyNewBorrowers() = runTest {
        val levels = first(FeedFilters(risk = Risk.Medium)).map { it.borrowerReputation.level }.toSet()

        assertEquals(setOf(ReputationLevel.EXCELLENT, ReputationLevel.TRUSTED, ReputationLevel.BUILDING), levels)
    }

    @Test
    fun noRiskFilterIncludesNewBorrowers() = runTest {
        val levels = first().map { it.borrowerReputation.level }.toSet()

        assertTrue(ReputationLevel.NEW in levels)
    }

    @Test
    fun theTermChipsReturnOnlyLoansInsideTheLimit() = runTest {
        for (chip in TermLimit.entries) {
            val loans = first(FeedFilters(term = chip))
            assertTrue(chip.name, loans.isNotEmpty())
            assertTrue(chip.name, loans.all { it.termDays <= chip.days })
        }
    }

    @Test
    fun theAmountRangesReturnOnlyPrincipalsInsideTheirBounds() = runTest {
        val hundred = TokenAmount.ofTokens(100).baseUnits
        val fiveHundred = TokenAmount.ofTokens(500).baseUnits

        val under = first(FeedFilters(amount = AmountRange.Under100))
        val between = first(FeedFilters(amount = AmountRange.From100To500))
        val over = first(FeedFilters(amount = AmountRange.Over500))

        assertTrue(under.isNotEmpty() && under.all { it.principal < hundred })
        assertTrue(between.isNotEmpty() && between.all { it.principal >= hundred && it.principal <= fiveHundred })
        // The seed has no request above 500 tUSD: the range is empty, not wrong.
        assertTrue(over.all { it.principal > fiveHundred })
    }

    @Test
    fun theBoundaryAmountsLandInExactlyOneRange() = runTest {
        val edges = listOf("99.999999", "100", "500", "500.000001").map { tokens ->
            val units = java.math.BigDecimal(tokens).movePointRight(6).toBigIntegerExact()
            FakeData.loan(clock, 1, FakeData.LUCIA, null, 1, 1000, 30, 1, LoanStatus.OPEN, requestedDaysAgo = 1)
                .copy(principal = units)
        }
        val boundary = FakeLoanRepository(edges.mapIndexed { index, loan -> loan.copy(loanId = java.math.BigInteger.valueOf(index + 1L)) })

        suspend fun ids(range: AmountRange) =
            boundary.list(FeedQueryMapper.toQuery(FeedFilters(amount = range))).getOrThrow().items.map { it.loanId.toInt() }.sorted()

        assertEquals(listOf(1), ids(AmountRange.Under100))
        assertEquals(listOf(2, 3), ids(AmountRange.From100To500))
        assertEquals(listOf(4), ids(AmountRange.Over500))
    }

    @Test
    fun eachSortOrdersTheOpenRequestsAsTheApiDoes() = runTest {
        val byRate = first(FeedFilters(sort = FeedSort.Rate)).map { it.interestRateBps }
        val byTerm = first(FeedFilters(sort = FeedSort.Term)).map { it.termDays }
        val byRecent = first(FeedFilters(sort = FeedSort.Recent)).map { it.requestedAt }

        assertEquals(byRate.sortedDescending(), byRate)
        assertEquals(byTerm.sorted(), byTerm)
        assertEquals(byRecent.sortedDescending(), byRecent)
    }
}
