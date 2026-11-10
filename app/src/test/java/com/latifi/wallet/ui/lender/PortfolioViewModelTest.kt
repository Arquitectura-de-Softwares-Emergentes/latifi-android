package com.latifi.wallet.ui.lender

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.core.LoanMath
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
class PortfolioViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS
    private val rateRepo = FakeExchangeRateRepository(clock)

    private fun TestScope.viewModel(
        repo: FakeLoanRepository = FakeLoanRepository(clock),
        handle: SavedStateHandle = SavedStateHandle(),
    ): PortfolioViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = PortfolioViewModel(handle, repo, LocalValueProvider(rateRepo, clock), FakePrefsStore(), sessions)
        runCurrent()
        return vm
    }

    /** A loan the demo wallet lent. [daysAgo] moves the funding time, so the next due date moves with it. */
    private fun lent(id: Long, status: LoanStatus, paid: Int = 0, daysAgo: Long = id): Loan =
        FakeData.loan(
            clock, id, FakeData.LUCIA, FakeData.DEMO_ADDRESS, 100, 1000, 30, 2, status,
            paid = paid, requestedDaysAgo = daysAgo + 1, fundedDaysAgo = daysAgo,
        )

    private fun PortfolioViewModel.items() = (state.value.content as PortfolioContent.Items)

    @Test
    fun theQueryHasTheLenderSortNextDueAndSize20() = runTest {
        val repo = FakeLoanRepository(clock)
        viewModel(repo)

        val query = repo.queries.single()
        assertEquals(address, query.lender)
        assertEquals(LoanSort.NextDue, query.sort)
        assertEquals("nextDue", query.sort.wire)
        assertEquals(20, query.size)
        assertEquals(0, query.page)
        assertNull(query.status)
        assertNull(query.borrower)
    }

    @Test
    fun theFourChipsAreAllPendingPaidAndOverdue() {
        assertEquals(
            listOf(null, LoanStatus.ACTIVE, LoanStatus.REPAID, LoanStatus.DEFAULTED),
            PortfolioFilter.entries.map { it.status },
        )
        assertEquals(PortfolioFilter.All, PortfolioUiStateDefaults.filter)
    }

    @Test
    fun eachChipMapsToTheExactStatusParameter() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)
        val expected = mapOf(
            PortfolioFilter.Pending to LoanStatus.ACTIVE,
            PortfolioFilter.Paid to LoanStatus.REPAID,
            PortfolioFilter.Overdue to LoanStatus.DEFAULTED,
        )
        for ((filter, status) in expected) {
            vm.onEvent(PortfolioEvent.SelectFilter(filter))
            runCurrent()
            val query = repo.queries.last()
            assertEquals(filter.name, status, query.status)
            assertEquals(address, query.lender)
            assertEquals(LoanSort.NextDue, query.sort)
            assertEquals(20, query.size)
            assertEquals(0, query.page)
            assertEquals(filter, vm.state.value.filter)
        }
        vm.onEvent(PortfolioEvent.SelectFilter(PortfolioFilter.All))
        runCurrent()
        assertNull(repo.queries.last().status)
    }

    @Test
    fun selectingTheSameChipAgainAsksNothing() = runTest {
        val repo = FakeLoanRepository(clock)
        val vm = viewModel(repo)

        vm.onEvent(PortfolioEvent.SelectFilter(PortfolioFilter.All))
        runCurrent()

        assertEquals(1, repo.queries.size)
    }

    @Test
    fun oneInvestmentMapsToTheExpectedCardModel() = runTest {
        val loan = lent(31, LoanStatus.ACTIVE, paid = 1, daysAgo = 12)
        val card = viewModel(FakeLoanRepository(listOf(loan))).items().cards.single()

        assertEquals(BigInteger.valueOf(31), card.loanId)
        assertEquals(TokenAmount.ofTokens(100), card.lent)
        assertEquals(TokenAmount(loan.totalDue), card.expected)
        assertEquals(TokenAmount(LoanMath.totalDue(TokenAmount.ofTokens(100).baseUnits, 1000, 30)), card.expected)
        assertEquals(UiLoanStatus.Pending, card.status)
        assertEquals(loan.nextDueDate, card.nextDue)
    }

    @Test
    fun theDemoSeedShowsTheTwoLoansOfTheLenderNextDueFirst() = runTest {
        val vm = viewModel()

        // Loan 8 is running and has a next due date. Loan 9 is repaid and has none, so it goes last.
        assertEquals(listOf(8L, 9L), vm.items().cards.map { it.loanId.toLong() })
        assertEquals(2L, vm.state.value.counter)
        assertEquals(UiLoanStatus.Pending, vm.items().cards.first().status)
        assertEquals(UiLoanStatus.Paid, vm.items().cards.last().status)
        assertNull(vm.items().cards.last().nextDue)
    }

    @Test
    fun theSoonestNextInstallmentComesFirst() = runTest {
        val repo = FakeLoanRepository(
            listOf(
                lent(1, LoanStatus.ACTIVE, daysAgo = 5),
                lent(2, LoanStatus.ACTIVE, daysAgo = 20),
                lent(3, LoanStatus.ACTIVE, daysAgo = 10),
            ),
        )

        assertEquals(listOf(2L, 3L, 1L), viewModel(repo).items().cards.map { it.loanId.toLong() })
    }

    @Test
    fun loadMoreAppendsPageOneAndStopsWhenThereIsNoMore() = runTest {
        val repo = FakeLoanRepository((1L..25L).map { lent(it, LoanStatus.REPAID, paid = 2, daysAgo = it + 5) })
        val vm = viewModel(repo)

        assertEquals(20, vm.items().cards.size)
        assertTrue(vm.items().hasMore)
        assertEquals(25L, vm.state.value.counter)

        vm.onEvent(PortfolioEvent.LoadMore)
        runCurrent()

        assertEquals(25, vm.items().cards.size)
        assertFalse(vm.items().hasMore)
        assertEquals(1, repo.queries.last().page)

        vm.onEvent(PortfolioEvent.LoadMore)
        runCurrent()
        assertEquals(2, repo.queries.size)
    }

    @Test
    fun changingTheChipResetsPagingAndTheScrollToken() = runTest {
        val repo = FakeLoanRepository((1L..25L).map { lent(it, LoanStatus.REPAID, paid = 2, daysAgo = it + 5) })
        val vm = viewModel(repo)
        vm.onEvent(PortfolioEvent.LoadMore)
        runCurrent()
        val token = vm.state.value.resetToken

        vm.onEvent(PortfolioEvent.SelectFilter(PortfolioFilter.Paid))
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
        vm.onEvent(PortfolioEvent.Refresh)
        runCurrent()

        assertEquals(before, vm.items().cards)
        assertTrue(vm.state.value.refreshFailed)
        assertFalse(vm.state.value.isRefreshing)

        vm.onEvent(PortfolioEvent.RefreshFailedShown)
        runCurrent()
        assertFalse(vm.state.value.refreshFailed)
    }

    @Test
    fun aFirstLoadFailureGivesTheErrorStateAndNoSnackbar() = runTest {
        val repo = FakeLoanRepository(clock).apply { failNext = AppError.Server }
        val vm = viewModel(repo)

        assertEquals(PortfolioContent.Error(ErrorKind.Server), vm.state.value.content)
        assertFalse(vm.state.value.refreshFailed)
        assertNull(vm.state.value.counter)

        vm.onEvent(PortfolioEvent.Reload)
        runCurrent()
        assertTrue(vm.state.value.content is PortfolioContent.Items)
    }

    @Test
    fun emptyAndFilteredEmptyAreDistinguished() = runTest {
        val none = viewModel(FakeLoanRepository(emptyList()))
        assertEquals(PortfolioContent.Empty, none.state.value.content)
        assertEquals(0L, none.state.value.counter)

        val onlyRunning = viewModel(FakeLoanRepository(listOf(lent(1, LoanStatus.ACTIVE))))
        assertTrue(onlyRunning.state.value.content is PortfolioContent.Items)
        onlyRunning.onEvent(PortfolioEvent.SelectFilter(PortfolioFilter.Paid))
        runCurrent()
        assertEquals(PortfolioContent.FilteredEmpty, onlyRunning.state.value.content)

        onlyRunning.onEvent(PortfolioEvent.ShowAll)
        runCurrent()
        assertEquals(PortfolioFilter.All, onlyRunning.state.value.filter)
        assertTrue(onlyRunning.state.value.content is PortfolioContent.Items)
    }

    @Test
    fun theCounterFollowsTheLatestChipEvenWhenAnOldAnswerArrivesLast() = runTest {
        val repo = FakeLoanRepository(
            listOf(lent(1, LoanStatus.ACTIVE), lent(2, LoanStatus.ACTIVE), lent(3, LoanStatus.REPAID, paid = 2, daysAgo = 9)),
        )
        val gate = CompletableDeferred<Unit>()
        repo.beforeList = { query -> if (query.status == null) gate.await() }
        val vm = viewModel(repo)
        assertNull(vm.state.value.counter)

        vm.onEvent(PortfolioEvent.SelectFilter(PortfolioFilter.Paid))
        runCurrent()
        assertEquals(1L, vm.state.value.counter)

        gate.complete(Unit)
        runCurrent()
        assertEquals(1L, vm.state.value.counter)
        assertEquals(1, vm.items().cards.size)
        assertEquals(PortfolioFilter.Paid, vm.state.value.filter)
    }

    @Test
    fun theChipSurvivesASavedStateRestore() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        first.onEvent(PortfolioEvent.SelectFilter(PortfolioFilter.Paid))
        runCurrent()

        val repo = FakeLoanRepository(clock)
        val restored = viewModel(repo, SavedStateHandle(mapOf("portfolio_filter" to handle.get<String>("portfolio_filter"))))

        assertEquals(PortfolioFilter.Paid, restored.state.value.filter)
        assertEquals(LoanStatus.REPAID, repo.queries.first().status)
        assertEquals(listOf(9L), restored.items().cards.map { it.loanId.toLong() })
    }

    @Test
    fun aRefreshAlsoAsksForFreshRates() = runTest {
        val vm = viewModel()
        assertEquals(1, rateRepo.ratesCalls)

        vm.onEvent(PortfolioEvent.Refresh)
        runCurrent()

        assertEquals(2, rateRepo.ratesCalls)
    }

    @Test
    fun theCurrencyOfThePreferencesReachesTheState() = runTest {
        assertEquals(com.latifi.wallet.core.LocalCurrency.PEN, viewModel().state.value.currency)
    }

    private object PortfolioUiStateDefaults {
        val filter = PortfolioUiState().filter
    }
}
