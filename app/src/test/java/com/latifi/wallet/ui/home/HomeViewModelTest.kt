package com.latifi.wallet.ui.home

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeChainReadRepository
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeReputationRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.chain.FeePolicy
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.BorrowerReputation
import com.latifi.wallet.domain.model.InstallmentSchedule
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.profile.SessionHints
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
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
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS
    private val rateRepo = FakeExchangeRateRepository(clock)

    private val loans = FakeLoanRepository(clock)
    private val chain = FakeChainReadRepository { loans.loans }
    private val reputation = FakeReputationRepository(clock)
    private val hints = SessionHints()
    private val overlays = OverlayController()
    private val prefs = FakePrefsStore(role = AppRole.Borrower)

    private fun TestScope.viewModel(
        chain: FakeChainReadRepository = this@HomeViewModelTest.chain,
        hints: SessionHints = this@HomeViewModelTest.hints,
        prefs: FakePrefsStore = this@HomeViewModelTest.prefs,
    ): HomeViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = HomeViewModel(
            chain = chain,
            loans = loans,
            reputation = reputation,
            sessions = sessions,
            rates = LocalValueProvider(rateRepo, clock),
            overlays = overlays,
            hints = hints,
            prefs = prefs,
        )
        runCurrent()
        return vm
    }

    private fun HomeViewModel.card(section: Loadable<NextDueCard?>): NextDueCard =
        (section as Loadable.Success).data!!

    @Test
    fun theBorrowerSectionsAreLoaded() = runTest {
        val vm = viewModel()
        val state = vm.state.value

        assertEquals(AppRole.Borrower, state.role)
        assertEquals(Loadable.Success(TokenAmount.ofTokens(1_000)), state.balance)
        assertEquals(Loadable.Success(BorrowerReputation(620, ReputationLevel.TRUSTED)), state.reputation)
        val loan = loans.loans.first { it.loanId == BigInteger.valueOf(11) }
        val card = vm.card(state.nextInstallment)
        assertEquals(loan.loanId, card.loanId)
        assertEquals(loan.nextDueDate, card.dueAt)
        // The lender sections are not loaded for a borrower.
        assertEquals(Loadable.Loading, state.openRequests)
        assertEquals(Loadable.Loading, state.nextPayment)
        assertTrue(state.rates is RatesState.Ready)
    }

    @Test
    fun theLenderSectionsAreLoaded() = runTest {
        val vm = viewModel(prefs = FakePrefsStore(role = AppRole.Lender))
        val state = vm.state.value

        assertEquals(AppRole.Lender, state.role)
        assertEquals(Loadable.Success(TokenAmount.ofTokens(1_000)), state.balance)
        val openCount = loans.loans.count { it.status == LoanStatus.OPEN }.toLong()
        assertEquals(Loadable.Success(openCount), state.openRequests)
        val card = vm.card(state.nextPayment)
        assertEquals(BigInteger.valueOf(8), card.loanId)
        assertEquals(Loadable.Loading, state.reputation)
        assertEquals(Loadable.Loading, state.nextInstallment)
    }

    @Test
    fun theLenderQueriesAskForTheCountAndTheNextPaymentOfTheSessionAddress() = runTest {
        viewModel(prefs = FakePrefsStore(role = AppRole.Lender))

        val open = loans.queries.single { it.status == LoanStatus.OPEN }
        assertEquals(1, open.size)
        val next = loans.queries.single { it.status == LoanStatus.ACTIVE }
        assertEquals(address, next.lender)
        assertEquals(LoanSort.NextDue, next.sort)
        assertEquals(1, next.size)
    }

    @Test
    fun aLenderWithNoActiveLoanSeesNoPaymentExpected() = runTest {
        loans.loans = loans.loans.filter { it.lender?.lowercase() != address.lowercase() }
        val vm = viewModel(prefs = FakePrefsStore(role = AppRole.Lender))

        assertEquals(Loadable.Success(null), vm.state.value.nextPayment)
    }

    @Test
    fun aBorrowerWithNoActiveLoanSeesNoInstallmentDue() = runTest {
        loans.loans = loans.loans.filter { it.status != LoanStatus.ACTIVE }
        val vm = viewModel()

        assertEquals(Loadable.Success(null), vm.state.value.nextInstallment)
    }

    @Test
    fun everyReadUsesTheSessionAddressAndNoOther() = runTest {
        val chain = FakeChainReadRepository { loans.loans }
        viewModel(chain = chain)

        assertTrue(chain.reads.isNotEmpty())
        assertTrue(chain.reads.all { it.second == address.lowercase() })
        assertTrue(reputation.calls.all { it.first == address.lowercase() })
        assertEquals(address, loans.queries.single { it.status == LoanStatus.ACTIVE }.borrower)
    }

    @Test
    fun oneFailingSectionLeavesTheOthersAlone() = runTest {
        loans.failNext = AppError.Offline
        val vm = viewModel()
        val state = vm.state.value

        assertEquals(Loadable.Error(ErrorKind.Offline), state.nextInstallment)
        assertTrue(state.balance is Loadable.Success)
        assertTrue(state.reputation is Loadable.Success)
    }

    @Test
    fun aFailingBalanceReadDoesNotHideTheReputationOrTheInstallment() = runTest {
        val failing = FakeChainReadRepository { loans.loans }
        failing.failNext(FakeChainReadRepository.Read.TokenBalance, AppError.Server)
        val vm = viewModel(chain = failing)
        val state = vm.state.value

        assertEquals(Loadable.Error(ErrorKind.Server), state.balance)
        assertTrue(state.reputation is Loadable.Success)
        assertTrue(state.nextInstallment is Loadable.Success)
    }

    @Test
    fun aFailingReputationReadIsAnErrorInThatSectionOnly() = runTest {
        reputation.failNext = AppError.Offline
        val vm = viewModel()
        val state = vm.state.value

        assertEquals(Loadable.Error(ErrorKind.Offline), state.reputation)
        assertTrue(state.balance is Loadable.Success)
    }

    @Test
    fun reloadRunsOnlyThatSection() = runTest {
        loans.failNext = AppError.Offline
        val vm = viewModel()
        val reputationCalls = reputation.calls.size

        vm.onEvent(HomeEvent.Reload(HomeSection.NextInstallment))
        runCurrent()

        assertTrue(vm.state.value.nextInstallment is Loadable.Success)
        assertEquals(reputationCalls, reputation.calls.size)
    }

    @Test
    fun noReputationYetGivesNoChipAndNoError() = runTest {
        reputation.remove(address)
        val vm = viewModel()

        assertEquals(Loadable.Success(null), vm.state.value.reputation)
    }

    @Test
    fun theLowFeeBannerShowsOnlyStrictlyBelowTheThreshold() = runTest {
        val threshold = FeePolicy.LOW_FEE_THRESHOLD_WEI
        val cases = listOf(
            threshold - BigInteger.ONE to true,
            threshold to false,
            threshold + BigInteger.ONE to false,
            BigInteger.ZERO to true,
        )
        cases.forEach { (wei, expected) ->
            val scripted = FakeChainReadRepository { loans.loans }
            scripted.setPolBalance(address, wei)
            val vm = viewModel(chain = scripted, hints = SessionHints())

            assertEquals("pol balance $wei", expected, vm.state.value.lowFee)
        }
    }

    @Test
    fun aFailedFeeReadShowsNoBanner() = runTest {
        val scripted = FakeChainReadRepository { loans.loans }
        scripted.setPolBalance(address, BigInteger.ZERO)
        scripted.failNext(FakeChainReadRepository.Read.PolBalance, AppError.Offline)
        val vm = viewModel(chain = scripted)

        assertFalse(vm.state.value.lowFee)
        assertTrue(vm.state.value.balance is Loadable.Success)
    }

    @Test
    fun theGreetingIsShownOnceAndASecondCollectionGetsNothing() = runTest {
        hints.queueGreeting("Ana")

        val first = viewModel()
        val second = viewModel()

        assertEquals("Ana", first.state.value.greeting)
        assertNull(second.state.value.greeting)
        assertNull(hints.greetingName.value)
    }

    @Test
    fun noQueuedGreetingMeansNoGreeting() = runTest {
        assertNull(viewModel().state.value.greeting)
    }

    @Test
    fun aZeroBalanceIsASuccessThatTheScreenExplains() = runTest {
        val empty = FakeChainReadRepository { loans.loans }
        empty.setTokenBalance(address, TokenAmount(BigInteger.ZERO))
        val vm = viewModel(chain = empty)

        val balance = vm.state.value.balance as Loadable.Success
        assertEquals(0, balance.data.baseUnits.signum())
    }

    @Test
    fun theNextInstallmentOfAPartiallyPaidLoanIsWhatIsLeftOnTheCurrentOne() = runTest {
        val loan = loans.loans.first { it.loanId == BigInteger.valueOf(11) }
        val half = InstallmentSchedule.build(loan)[1].amount.divide(BigInteger.TWO)
        val partial = loan.copy(amountRepaid = loan.amountRepaid + half)
        loans.loans = loans.loans.map { if (it.loanId == loan.loanId) partial else it }

        val vm = viewModel()

        val card = vm.card(vm.state.value.nextInstallment)
        val expected = InstallmentSchedule.build(partial)[1].amount - half
        assertEquals(TokenAmount(expected), card.amount)
        assertEquals(TokenAmount(InstallmentSchedule.remainingOnCurrent(partial)), card.amount)
    }

    @Test
    fun getCoinsAsksForTheCoinsSheet() = runTest {
        val vm = viewModel()

        vm.onEvent(HomeEvent.GetCoins)

        assertEquals(Overlays.COINS, overlays.current.value)
    }

    @Test
    fun aRoleChangeLoadsTheSectionsOfTheNewRole() = runTest {
        val changing = FakePrefsStore(role = AppRole.Borrower)
        val vm = viewModel(prefs = changing)
        assertEquals(Loadable.Loading, vm.state.value.openRequests)

        runBlocking { changing.setRole(AppRole.Lender) }
        runCurrent()

        assertEquals(AppRole.Lender, vm.state.value.role)
        assertTrue(vm.state.value.openRequests is Loadable.Success)
        assertTrue(vm.state.value.nextPayment is Loadable.Success)
    }

    @Test
    fun reloadingAfterABalanceFailureShowsTheBalance() = runTest {
        val flaky = FakeChainReadRepository { loans.loans }
        flaky.failNext(FakeChainReadRepository.Read.TokenBalance, AppError.Server)
        val vm = viewModel(chain = flaky)
        assertEquals(Loadable.Error(ErrorKind.Server), vm.state.value.balance)

        vm.onEvent(HomeEvent.Reload(HomeSection.Balance))
        runCurrent()

        assertEquals(Loadable.Success(TokenAmount.ofTokens(1_000)), vm.state.value.balance)
    }
}
