package com.latifi.wallet.ui.loans

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.UiLoanStatus
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.ui.common.ErrorKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class LoansViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS
    private val rateRepo = FakeExchangeRateRepository(clock)

    private fun TestScope.viewModel(
        repo: FakeLoanRepository = FakeLoanRepository(clock),
        handle: SavedStateHandle = SavedStateHandle(),
    ): LoansViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = LoansViewModel(handle, repo, LocalValueProvider(rateRepo, clock), FakePrefsStore(), sessions)
        runCurrent()
        return vm
    }

    private fun own(id: Long, status: LoanStatus, paid: Int = 0, daysAgo: Long = id): Loan =
        FakeData.loan(
            clock, id, FakeData.DEMO_ADDRESS, if (status == LoanStatus.OPEN) null else FakeData.LENDER_A, 100, 1000, 30, 2, status,
            paid = paid, requestedDaysAgo = daysAgo, fundedDaysAgo = if (status == LoanStatus.OPEN) null else daysAgo - 1,
        )

    private fun LoansViewModel.items() = (state.value.content as LoansContent.Items)

    @Test
    fun theQueryHasTheBorrowerSortRecentAndSize20() = runTest {
        val repo = FakeLoanRepository(clock)
        viewModel(repo)

        val query = repo.queries.single()
        assertEquals(address, query.borrower)
        assertEquals(LoanSort.Recent, query.sort)
        assertEquals(20, query.size)
        assertEquals(0, query.page)
        assertNull(query.status)
    }

    @Test
    fun oneLoanMapsToTheExpectedCardModel() = runTest {
        val loan = FakeData.loan(
            clock, 11, FakeData.DEMO_ADDRESS, FakeData.LENDER_A, 250, 1000, 30, 3, LoanStatus.ACTIVE, paid = 1,
            requestedDaysAgo = 13, fundedDaysAgo = 12,
        )
        val card = viewModel(FakeLoanRepository(listOf(loan))).items().cards.single()

        assertEquals(BigInteger.valueOf(11), card.loanId)
        assertEquals(TokenAmount.ofTokens(250), card.amount)
        assertEquals(UiLoanStatus.Pending, card.status)
        assertEquals(1000, card.rateBps)
        assertEquals(30, card.termDays)
        assertEquals(3, card.installments)
        assertEquals(1, card.installmentsPaid)
        assertEquals(loan.nextDueDate, card.nextDue)
    }

    @Test
    fun theDemoSeedShowsTheFiveBorrowerLoansNewestFirst() = runTest {
        val vm = viewModel()

        assertEquals(listOf(12L, 11L, 5L, 7L, 3L), vm.items().cards.map { it.loanId.toLong() })
        assertEquals(5L, vm.state.value.counter)
    }

    @Test
    fun eachChipMapsToTheExactStatusParameter() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        val expected = mapOf(
            LoanFilter.All to null,
            LoanFilter.Open to LoanStatus.OPEN,
            LoanFilter.Pending to LoanStatus.ACTIVE,
            LoanFilter.Paid to LoanStatus.REPAID,
            LoanFilter.Overdue to LoanStatus.DEFAULTED,
        )
        for ((filter, status) in expected) {
            if (filter == LoanFilter.All) continue
            vm.onEvent(LoansEvent.SelectFilter(filter))
            runCurrent()
            val query = repo.queries.last()
            assertEquals(filter.name, status, query.status)
            assertEquals(address, query.borrower)
            assertEquals(LoanSort.Recent, query.sort)
            assertEquals(20, query.size)
            assertEquals(0, query.page)
            assertEquals(filter, vm.state.value.filter)
        }
        vm.onEvent(LoansEvent.SelectFilter(LoanFilter.All))
        runCurrent()
        assertNull(repo.queries.last().status)
    }

    @Test
    fun selectingTheSameChipAgainAsksNothing() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)

        vm.onEvent(LoansEvent.SelectFilter(LoanFilter.All))
        runCurrent()

        assertEquals(1, repo.queries.size)
    }

    @Test
    fun loadMoreAppendsPageOneAndStopsWhenThereIsNoMore() = runTest {
        val repo = FakeLoanRepository((1L..25L).map { own(it, LoanStatus.REPAID, paid = 2, daysAgo = it + 5) })
        val vm = viewModel(repo)

        assertEquals(20, vm.items().cards.size)
        assertTrue(vm.items().hasMore)
        assertEquals(25L, vm.state.value.counter)

        vm.onEvent(LoansEvent.LoadMore)
        runCurrent()

        assertEquals(25, vm.items().cards.size)
        assertFalse(vm.items().hasMore)
        assertEquals(1, repo.queries.last().page)

        vm.onEvent(LoansEvent.LoadMore)
        runCurrent()
        assertEquals(2, repo.queries.size)
    }

    @Test
    fun changingTheFilterResetsPagingAndTheScrollToken() = runTest {
        val repo = FakeLoanRepository((1L..25L).map { own(it, LoanStatus.REPAID, paid = 2, daysAgo = it + 5) })
        val vm = viewModel(repo)
        vm.onEvent(LoansEvent.LoadMore)
        runCurrent()
        val token = vm.state.value.resetToken

        vm.onEvent(LoansEvent.SelectFilter(LoanFilter.Paid))
        runCurrent()

        assertEquals(0, repo.queries.last().page)
        assertEquals(20, vm.items().cards.size)
        assertEquals(token + 1, vm.state.value.resetToken)
    }

    @Test
    fun aRefreshFailureKeepsTheDataAndRaisesTheSnackbarOnce() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        val before = vm.items().cards

        repo.failNext = AppError.Offline
        vm.onEvent(LoansEvent.Refresh)
        runCurrent()

        assertEquals(before, vm.items().cards)
        assertTrue(vm.state.value.refreshFailed)
        assertFalse(vm.state.value.isRefreshing)

        vm.onEvent(LoansEvent.RefreshFailedShown)
        runCurrent()
        assertFalse(vm.state.value.refreshFailed)
    }

    @Test
    fun aFirstLoadFailureGivesTheErrorStateAndNoSnackbar() = runTest {
        val repo = FakeLoanRepository(clock).apply { failNext = AppError.Server }
        val vm = viewModel(repo)

        assertEquals(LoansContent.Error(ErrorKind.Server), vm.state.value.content)
        assertFalse(vm.state.value.refreshFailed)
        assertNull(vm.state.value.counter)

        vm.onEvent(LoansEvent.Reload)
        runCurrent()
        assertTrue(vm.state.value.content is LoansContent.Items)
    }

    @Test
    fun emptyAndFilteredEmptyAreDistinguished() = runTest {
        val none = viewModel(FakeLoanRepository(emptyList()))
        assertEquals(LoansContent.Empty, none.state.value.content)
        assertEquals(0L, none.state.value.counter)

        val onlyOpen = viewModel(FakeLoanRepository(listOf(own(1, LoanStatus.OPEN))))
        assertTrue(onlyOpen.state.value.content is LoansContent.Items)
        onlyOpen.onEvent(LoansEvent.SelectFilter(LoanFilter.Paid))
        runCurrent()
        assertEquals(LoansContent.FilteredEmpty, onlyOpen.state.value.content)

        onlyOpen.onEvent(LoansEvent.ShowAll)
        runCurrent()
        assertEquals(LoanFilter.All, onlyOpen.state.value.filter)
        assertTrue(onlyOpen.state.value.content is LoansContent.Items)
    }

    @Test
    fun theCounterFollowsTheLatestFilterEvenWhenAnOldAnswerArrivesLast() = runTest {
        val repo = FakeLoanRepository(
            listOf(own(1, LoanStatus.OPEN), own(2, LoanStatus.OPEN), own(3, LoanStatus.REPAID, paid = 2, daysAgo = 9)),
        )
        val gate = CompletableDeferred<Unit>()
        repo.beforeList = { query -> if (query.status == null) gate.await() }
        val vm = viewModel(repo)
        assertNull(vm.state.value.counter)

        vm.onEvent(LoansEvent.SelectFilter(LoanFilter.Paid))
        runCurrent()
        assertEquals(1L, vm.state.value.counter)

        gate.complete(Unit)
        runCurrent()
        assertEquals(1L, vm.state.value.counter)
        assertEquals(1, vm.items().cards.size)
        assertEquals(LoanFilter.Paid, vm.state.value.filter)
    }

    @Test
    fun theFilterSurvivesASavedStateRestore() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        first.onEvent(LoansEvent.SelectFilter(LoanFilter.Overdue))
        runCurrent()

        val repo = FakeLoanRepository(clock)
        val restored = viewModel(repo, SavedStateHandle(mapOf("loans_filter" to handle.get<String>("loans_filter"))))

        assertEquals(LoanFilter.Overdue, restored.state.value.filter)
        assertEquals(LoanStatus.DEFAULTED, repo.queries.first().status)
        assertEquals(listOf(5L), restored.items().cards.map { it.loanId.toLong() })
    }

    @Test
    fun aRefreshAlsoAsksForFreshRates() = runTest {
        val vm = viewModel()
        assertEquals(1, rateRepo.ratesCalls)

        vm.onEvent(LoansEvent.Refresh)
        runCurrent()

        assertEquals(2, rateRepo.ratesCalls)
    }

    @Test
    fun theCurrencyOfThePreferencesReachesTheState() = runTest {
        val vm = viewModel()
        assertEquals(com.latifi.wallet.core.LocalCurrency.PEN, vm.state.value.currency)
    }
}
