package com.latifi.wallet.domain.feed

import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** The UI-SPEC S12 mapping table, one assertion per chip. */
class FeedQueryMapperTest {
    private fun big(value: String) = BigInteger(value)

    @Test
    fun noFiltersAskForOpenRequestsByReputationWithTwentyPerPage() {
        val query = FeedQueryMapper.toQuery(FeedFilters())

        assertEquals(LoanStatus.OPEN, query.status)
        assertEquals(LoanSort.Reputation, query.sort)
        assertEquals(0, query.page)
        assertEquals(20, query.size)
        assertNull(query.minLevel)
        assertNull(query.maxTermDays)
        assertNull(query.minAmount)
        assertNull(query.maxAmount)
        assertNull(query.borrower)
        assertNull(query.lender)
    }

    @Test
    fun lowestRiskMapsToMinLevelExcellentAndNothingElse() {
        val query = FeedQueryMapper.toQuery(FeedFilters(risk = Risk.Lowest))

        assertEquals(ReputationLevel.EXCELLENT, query.minLevel)
        assertNull(query.maxTermDays)
        assertNull(query.minAmount)
        assertNull(query.maxAmount)
    }

    @Test
    fun lowRiskMapsToMinLevelTrusted() {
        assertEquals(ReputationLevel.TRUSTED, FeedQueryMapper.toQuery(FeedFilters(risk = Risk.Low)).minLevel)
    }

    @Test
    fun mediumRiskMapsToMinLevelBuilding() {
        assertEquals(ReputationLevel.BUILDING, FeedQueryMapper.toQuery(FeedFilters(risk = Risk.Medium)).minLevel)
    }

    @Test
    fun eachTermChipMapsToItsMaxTermDays() {
        assertEquals(30, FeedQueryMapper.toQuery(FeedFilters(term = TermLimit.UpTo30)).maxTermDays)
        assertEquals(45, FeedQueryMapper.toQuery(FeedFilters(term = TermLimit.UpTo45)).maxTermDays)
        assertEquals(60, FeedQueryMapper.toQuery(FeedFilters(term = TermLimit.UpTo60)).maxTermDays)
    }

    @Test
    fun underHundredMapsToMaxAmountOnly() {
        val query = FeedQueryMapper.toQuery(FeedFilters(amount = AmountRange.Under100))

        assertNull(query.minAmount)
        assertEquals(big("99999999"), query.maxAmount)
    }

    @Test
    fun hundredToFiveHundredMapsToBothBounds() {
        val query = FeedQueryMapper.toQuery(FeedFilters(amount = AmountRange.From100To500))

        assertEquals(big("100000000"), query.minAmount)
        assertEquals(big("500000000"), query.maxAmount)
    }

    @Test
    fun overFiveHundredMapsToMinAmountOnly() {
        val query = FeedQueryMapper.toQuery(FeedFilters(amount = AmountRange.Over500))

        assertEquals(big("500000001"), query.minAmount)
        assertNull(query.maxAmount)
    }

    @Test
    fun theAmountBoundsHaveNoGapAndNoOverlapAtTheEdges() {
        // 99.999999 tUSD is under 100, 100.000000 starts the middle range, 500.000000 ends it, 500.000001 is over.
        assertEquals(FeedQueryMapper.UNDER_100_MAX.add(BigInteger.ONE), FeedQueryMapper.FROM_100_MIN)
        assertEquals(FeedQueryMapper.TO_500_MAX.add(BigInteger.ONE), FeedQueryMapper.OVER_500_MIN)
    }

    @Test
    fun aCombinationOfTheThreeGroupsKeepsEveryParameter() {
        val query = FeedQueryMapper.toQuery(
            FeedFilters(risk = Risk.Low, term = TermLimit.UpTo45, amount = AmountRange.From100To500, sort = FeedSort.Rate),
        )

        assertEquals(LoanStatus.OPEN, query.status)
        assertEquals(ReputationLevel.TRUSTED, query.minLevel)
        assertEquals(45, query.maxTermDays)
        assertEquals(big("100000000"), query.minAmount)
        assertEquals(big("500000000"), query.maxAmount)
        assertEquals(LoanSort.Rate, query.sort)
    }

    @Test
    fun eachSortMapsToItsWireName() {
        val wire = FeedSort.entries.associateWith { FeedQueryMapper.toQuery(FeedFilters(sort = it)).sort.wire }

        assertEquals(
            mapOf(
                FeedSort.Reputation to "reputation",
                FeedSort.Rate to "rate",
                FeedSort.Term to "term",
                FeedSort.Recent to "recent",
            ),
            wire,
        )
    }

    @Test
    fun pageAndSizeComeFromTheCaller() {
        val query = FeedQueryMapper.toQuery(FeedFilters(), page = 3, size = 50)

        assertEquals(3, query.page)
        assertEquals(50, query.size)
    }

    @Test
    fun theSortNeverCountsAsAnActiveFilter() {
        assertEquals(0, FeedFilters(sort = FeedSort.Term).activeCount)
        assertFalse(FeedFilters(sort = FeedSort.Recent).hasActive)
        assertEquals(3, FeedFilters(Risk.Low, TermLimit.UpTo30, AmountRange.Over500).activeCount)
    }

    @Test
    fun choosingTheSameChipAgainClearsItsGroup() {
        val once = FeedFilters().toggleRisk(Risk.Low).toggleTerm(TermLimit.UpTo30).toggleAmount(AmountRange.Under100)
        assertEquals(Risk.Low, once.risk)

        val twice = once.toggleRisk(Risk.Low).toggleTerm(TermLimit.UpTo30).toggleAmount(AmountRange.Under100)
        assertFalse(twice.hasActive)
    }

    @Test
    fun choosingAnotherChipOfTheSameGroupReplacesIt() {
        val filters = FeedFilters().toggleRisk(Risk.Low).toggleRisk(Risk.Lowest)

        assertEquals(Risk.Lowest, filters.risk)
    }

    @Test
    fun clearedResetsTheThreeGroupsAndKeepsTheSort() {
        val cleared = FeedFilters(Risk.Medium, TermLimit.UpTo60, AmountRange.Over500, FeedSort.Term).cleared()

        assertTrue(cleared.risk == null && cleared.term == null && cleared.amount == null)
        assertEquals(FeedSort.Term, cleared.sort)
    }
}
