package com.latifi.wallet.ui.feed

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import app.cash.turbine.test
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.feed.AmountRange
import com.latifi.wallet.domain.feed.FeedFilters
import com.latifi.wallet.domain.feed.FeedQueryMapper
import com.latifi.wallet.domain.feed.FeedSort
import com.latifi.wallet.domain.feed.Risk
import com.latifi.wallet.domain.feed.TermLimit
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS
    private val rateRepo = FakeExchangeRateRepository(clock)

    private fun TestScope.viewModel(
        repo: FakeLoanRepository = FakeLoanRepository(clock),
        handle: SavedStateHandle = SavedStateHandle(),
        profiles: FakeProfileRepository = FakeProfileRepository(clock),
    ): FeedViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = FeedViewModel(handle, repo, profiles, LocalValueProvider(rateRepo, clock), FakePrefsStore(), sessions)
        runCurrent()
        return vm
    }

    private fun FeedViewModel.items() = state.value.content as FeedContent.Items

    @Test
    fun theDefaultQueryIsOpenSortedByReputationWithTwentyPerPage() = runTest {
        val repo = FakeLoanRepository(clock)
        viewModel(repo)

        val query = repo.queries.single()
        assertEquals(LoanStatus.OPEN, query.status)
        assertEquals(LoanSort.Reputation, query.sort)
        assertEquals(20, query.size)
        assertEquals(0, query.page)
        assertNull(query.borrower)
        assertNull(query.minLevel)
    }

    @Test
    fun theCardsComeHighestReputationFirstAndTheCounterIsTheTotal() = runTest {
        val vm = viewModel()

        assertEquals(listOf(1L, 2L, 12L, 10L, 4L, 6L), vm.items().cards.map { it.loanId.toLong() })
        assertEquals(6L, vm.state.value.counter)
    }

    @Test
    fun theCounterIsTotalItemsAndNotThePageSize() = runTest {
        val many = (1L..25L).map {
            FakeData.loan(clock, it, FakeData.LUCIA, null, 100, 1000, 30, 2, LoanStatus.OPEN, requestedDaysAgo = it)
        }
        val vm = viewModel(FakeLoanRepository(many))

        assertEquals(20, vm.items().cards.size)
        assertEquals(25L, vm.state.value.counter)
        assertTrue(vm.items().hasMore)
    }

    @Test
    fun aCardCarriesAmountReputationRateTermAndInstallments() = runTest {
        val card = viewModel().items().cards.first()

        assertEquals(BigInteger.ONE, card.loanId)
        assertEquals(TokenAmount.ofTokens(500), card.amount)
        assertEquals(ReputationLevel.EXCELLENT, card.level)
        assertEquals(820, card.score)
        assertEquals(700, card.rateBps)
        assertEquals(60, card.termDays)
        assertEquals(6, card.installments)
        assertFalse(card.isOwn)
    }

    @Test
    fun theNameIsNullUntilTheProfileAnswersAndThenFillsIn() = runTest {
        val vm = viewModel()
        runCurrent()

        val byId = vm.items().cards.associateBy { it.loanId.toLong() }
        assertEquals("Lucia Fernandez", byId.getValue(1L).name)
        assertEquals("Mateo Quispe", byId.getValue(2L).name)
        // The new borrower has no public profile, so the card keeps the fallback.
        assertNull(byId.getValue(6L).name)
    }

    @Test
    fun theOwnRequestIsMarked() = runTest {
        val own = viewModel().items().cards.single { it.loanId == BigInteger.valueOf(12) }

        assertTrue(own.isOwn)
        assertEquals(TokenAmount.ofTokens(120), own.amount)
    }

    // The debounce, latest wins and the counter.

    private fun TestScope.settle() {
        advanceTimeBy(FeedViewModel.DEBOUNCE_MS)
        runCurrent()
    }

    @Test
    fun threeFilterChangesInsideTheQuietPeriodIssueOneFetchWithTheFinalSet() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        assertEquals(1, repo.queries.size)

        vm.onEvent(FeedEvent.SelectRisk(Risk.Medium))
        advanceTimeBy(150)
        vm.onEvent(FeedEvent.SelectTerm(TermLimit.UpTo45))
        advanceTimeBy(150)
        vm.onEvent(FeedEvent.SelectAmount(AmountRange.Under100))
        advanceTimeBy(FeedViewModel.DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals("nothing goes out before the quiet period ends", 1, repo.queries.size)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, repo.queries.size)
        val query = repo.queries.last()
        assertEquals(LoanStatus.OPEN, query.status)
        assertEquals(ReputationLevel.BUILDING, query.minLevel)
        assertEquals(45, query.maxTermDays)
        assertEquals(FeedQueryMapper.UNDER_100_MAX, query.maxAmount)
        assertEquals(1L, vm.state.value.counter)
    }

    @Test
    fun aSlowAnswerOfAnOldQueryNeverOverwritesTheCounterOfANewerOne() = runTest {
        val repo = FakeLoanRepository(clock)
        val gate = CompletableDeferred<Unit>()
        repo.beforeList = { query -> if (query.minLevel == ReputationLevel.TRUSTED) gate.await() }
        val vm = viewModel(repo)

        vm.onEvent(FeedEvent.SelectRisk(Risk.Low))
        settle()
        assertNull("the old request is still in flight", vm.state.value.counter)

        vm.onEvent(FeedEvent.SelectRisk(Risk.Lowest))
        settle()
        assertEquals(1L, vm.state.value.counter)
        assertEquals(listOf(1L), vm.items().cards.map { it.loanId.toLong() })

        gate.complete(Unit)
        runCurrent()
        assertEquals(1L, vm.state.value.counter)
        assertEquals(listOf(1L), vm.items().cards.map { it.loanId.toLong() })
        assertEquals(Risk.Lowest, vm.state.value.filters.risk)
    }

    @Test
    fun theCounterIsAnnouncedOncePerSettledFilterSet() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)

        vm.state.map { it.counter }.distinctUntilChanged().test {
            assertEquals(6L, awaitItem())

            vm.onEvent(FeedEvent.SelectRisk(Risk.Medium))
            advanceTimeBy(100)
            vm.onEvent(FeedEvent.SelectTerm(TermLimit.UpTo45))
            advanceTimeBy(100)
            vm.onEvent(FeedEvent.SelectAmount(AmountRange.Under100))
            settle()

            // Loading once, then the final number once. No number for a set that was replaced.
            assertNull(awaitItem())
            assertEquals(1L, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun theCounterIsLoadingWhileTheRequestIsInFlight() = runTest {
        val repo = FakeLoanRepository(clock)
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(repo)
        repo.beforeList = { gate.await() }

        vm.onEvent(FeedEvent.SelectRisk(Risk.Low))
        settle()
        assertNull(vm.state.value.counter)
        assertEquals(FeedContent.Loading, vm.state.value.content)

        gate.complete(Unit)
        runCurrent()
        assertEquals(3L, vm.state.value.counter)
    }

    @Test
    fun aChipToggleOffWhenSelectedTwiceAndNothingNewIsAsked() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)

        vm.onEvent(FeedEvent.SelectRisk(Risk.Low))
        vm.onEvent(FeedEvent.SelectRisk(Risk.Low))
        assertNull(vm.state.value.filters.risk)
        settle()

        // The final set equals the applied one, so no request goes out and the number stays.
        assertEquals(1, repo.queries.size)
        assertEquals(6L, vm.state.value.counter)
    }

    @Test
    fun chooseAnotherChipOfTheSameGroupReplacesItInTheQuery() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)

        vm.onEvent(FeedEvent.SelectRisk(Risk.Low))
        settle()
        vm.onEvent(FeedEvent.SelectRisk(Risk.Lowest))
        settle()

        assertEquals(ReputationLevel.EXCELLENT, repo.queries.last().minLevel)
        assertEquals(1, vm.state.value.activeFilterCount)
    }

    // Clear, removal and counts.

    @Test
    fun clearFiltersResetsTheThreeGroupsKeepsTheSortAndAsksForFocusOnTheCounter() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        vm.onEvent(FeedEvent.SetSort(FeedSort.Rate))
        vm.onEvent(FeedEvent.SelectRisk(Risk.Low))
        vm.onEvent(FeedEvent.SelectTerm(TermLimit.UpTo30))
        vm.onEvent(FeedEvent.SelectAmount(AmountRange.Over500))
        settle()
        assertTrue(vm.state.value.hasActiveFilters)

        vm.effects.test {
            vm.onEvent(FeedEvent.ClearFilters)
            assertEquals(FeedEffect.FocusCounter, awaitItem())
            settle()

            val filters = vm.state.value.filters
            assertTrue(filters.risk == null && filters.term == null && filters.amount == null)
            assertEquals(FeedSort.Rate, filters.sort)
            assertFalse(vm.state.value.hasActiveFilters)
            val query = repo.queries.last()
            assertEquals(LoanSort.Rate, query.sort)
            assertNull(query.minLevel)
            assertNull(query.maxTermDays)
            assertNull(query.minAmount)
            assertNull(query.maxAmount)
            expectNoEvents()
        }
    }

    @Test
    fun clearFiltersIsNotOfferedWhenOnlyTheSortChanged() = runTest {
        val vm = viewModel()
        vm.onEvent(FeedEvent.SetSort(FeedSort.Term))
        settle()

        assertFalse(vm.state.value.hasActiveFilters)
        assertEquals(0, vm.state.value.activeFilterCount)
        vm.effects.test {
            vm.onEvent(FeedEvent.ClearFilters)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun activeFilterCountIgnoresTheSort() = runTest {
        val vm = viewModel()

        vm.onEvent(FeedEvent.SetSort(FeedSort.Recent))
        runCurrent()
        assertEquals(0, vm.state.value.activeFilterCount)
        vm.onEvent(FeedEvent.SelectRisk(Risk.Medium))
        vm.onEvent(FeedEvent.SelectAmount(AmountRange.Over500))
        runCurrent()
        assertEquals(2, vm.state.value.activeFilterCount)
        assertEquals(listOf(FilterGroup.Risk, FilterGroup.Amount), vm.state.value.activeGroups)
    }

    @Test
    fun removingAnActiveChipRefetchesAndAnnouncesIt() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        vm.onEvent(FeedEvent.SelectRisk(Risk.Low))
        vm.onEvent(FeedEvent.SelectTerm(TermLimit.UpTo60))
        settle()

        vm.effects.test {
            vm.onEvent(FeedEvent.RemoveFilter(FilterGroup.Risk))
            assertEquals(FeedEffect.FilterRemoved, awaitItem())
            settle()

            assertNull(vm.state.value.filters.risk)
            assertEquals(TermLimit.UpTo60, vm.state.value.filters.term)
            val query = repo.queries.last()
            assertNull(query.minLevel)
            assertEquals(60, query.maxTermDays)
        }
    }

    @Test
    fun theSortChangeAsksForTheNewOrderAndKeepsTheFilters() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        vm.onEvent(FeedEvent.SelectRisk(Risk.Medium))
        settle()

        vm.onEvent(FeedEvent.SetSort(FeedSort.Recent))
        settle()

        val query = repo.queries.last()
        assertEquals(LoanSort.Recent, query.sort)
        assertEquals(ReputationLevel.BUILDING, query.minLevel)
        assertEquals(FeedSort.Recent, vm.state.value.filters.sort)
    }

    // Paging, restore and the states.

    @Test
    fun aFilterChangeResetsPagingToPageZeroAndTheScrollToken() = runTest {
        val many = (1L..25L).map {
            FakeData.loan(clock, it, FakeData.LUCIA, null, 100, 1000, 30, 2, LoanStatus.OPEN, requestedDaysAgo = it)
        }
        val repo = FakeLoanRepository(many)
        val vm = viewModel(repo)
        vm.onEvent(FeedEvent.LoadMore)
        runCurrent()
        assertEquals(1, repo.queries.last().page)
        assertEquals(25, vm.items().cards.size)
        val token = vm.state.value.resetToken

        vm.onEvent(FeedEvent.SelectTerm(TermLimit.UpTo30))
        settle()

        assertEquals(0, repo.queries.last().page)
        assertEquals(20, vm.items().cards.size)
        assertEquals(token + 1, vm.state.value.resetToken)
    }

    @Test
    fun theFiltersRestoreFromTheSavedStateHandle() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        first.onEvent(FeedEvent.SelectRisk(Risk.Lowest))
        first.onEvent(FeedEvent.SelectTerm(TermLimit.UpTo60))
        first.onEvent(FeedEvent.SelectAmount(AmountRange.From100To500))
        first.onEvent(FeedEvent.SetSort(FeedSort.Rate))
        settle()

        val repo = FakeLoanRepository(clock)
        val restored = viewModel(
            repo,
            SavedStateHandle(
                mapOf(
                    "feed_risk" to handle.get<String>("feed_risk"),
                    "feed_term" to handle.get<String>("feed_term"),
                    "feed_amount" to handle.get<String>("feed_amount"),
                    "feed_sort" to handle.get<String>("feed_sort"),
                ),
            ),
        )

        assertEquals(FeedFilters(Risk.Lowest, TermLimit.UpTo60, AmountRange.From100To500, FeedSort.Rate), restored.state.value.filters)
        val query = repo.queries.single()
        assertEquals(ReputationLevel.EXCELLENT, query.minLevel)
        assertEquals(60, query.maxTermDays)
        assertEquals(LoanSort.Rate, query.sort)
    }

    @Test
    fun noOpenRequestsAtAllIsDifferentFromNoMatchForTheFilters() = runTest {
        val none = viewModel(FakeLoanRepository(emptyList()))
        assertEquals(FeedContent.Empty, none.state.value.content)
        assertEquals(0L, none.state.value.counter)

        val one = viewModel(FakeLoanRepository(listOf(FakeData.loan(clock, 1, FakeData.NEW_BORROWER, null, 50, 900, 30, 1, LoanStatus.OPEN, requestedDaysAgo = 1, score = 120))))
        assertTrue(one.state.value.content is FeedContent.Items)
        one.onEvent(FeedEvent.SelectRisk(Risk.Lowest))
        settle()
        assertEquals(FeedContent.FilteredEmpty, one.state.value.content)
        assertEquals(0L, one.state.value.counter)

        one.onEvent(FeedEvent.ClearFilters)
        settle()
        assertTrue(one.state.value.content is FeedContent.Items)
    }

    @Test
    fun aRefreshFailureWithDataKeepsTheListAndRaisesTheSnackbarOnce() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        val before = vm.items().cards

        repo.failNext = AppError.Offline
        vm.onEvent(FeedEvent.Refresh)
        runCurrent()

        assertEquals(before, vm.items().cards)
        assertTrue(vm.state.value.refreshFailed)
        assertFalse(vm.state.value.isRefreshing)

        vm.onEvent(FeedEvent.RefreshFailedShown)
        runCurrent()
        assertFalse(vm.state.value.refreshFailed)
    }

    @Test
    fun aFirstLoadFailureGivesTheErrorStateAndReloadRecovers() = runTest {
        val repo = FakeLoanRepository(clock).apply { failNext = AppError.Server }
        val vm = viewModel(repo)

        assertEquals(FeedContent.Error(ErrorKind.Server), vm.state.value.content)
        assertFalse(vm.state.value.refreshFailed)
        assertNull(vm.state.value.counter)

        vm.onEvent(FeedEvent.Reload)
        runCurrent()
        assertTrue(vm.state.value.content is FeedContent.Items)
    }

    @Test
    fun onlyTheOwnRequestIsMarkedSoFundIsDisabledThereAndNowhereElse() = runTest {
        val cards = viewModel().items().cards

        assertEquals(listOf(12L), cards.filter { it.isOwn }.map { it.loanId.toLong() })
    }

    @Test
    fun aMissingNameIsAskedAgainOnRefreshAndFillsInWhenTheProfileExists() = runTest {
        val profiles = FakeProfileRepository(clock, publicNames = emptyMap())
        val vm = viewModel(profiles = profiles)
        assertTrue(vm.items().cards.all { it.name == null })

        profiles.seed(Profile(FakeData.LUCIA, "Lucia Fernandez", null, FakeData.EPOCH, FakeData.EPOCH))
        vm.onEvent(FeedEvent.Refresh)
        runCurrent()

        assertEquals("Lucia Fernandez", vm.items().cards.first { it.borrower == FakeData.LUCIA }.name)
        assertNull(vm.items().cards.first { it.borrower == FakeData.MATEO }.name)
    }
}
