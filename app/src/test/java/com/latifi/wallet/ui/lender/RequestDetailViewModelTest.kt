package com.latifi.wallet.ui.lender

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeReputationRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.profile.ProfileGate
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.profile.runProfileGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RequestDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val rateRepo = FakeExchangeRateRepository(clock)
    private val reputation = FakeReputationRepository(clock)
    private val profiles = FakeProfileRepository(clock)

    private fun sessions(): InMemorySessionStore = InMemorySessionStore().also {
        runBlocking { it.save(Session(FakeWalletGateway.DEMO_ADDRESS, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
    }

    private fun TestScope.viewModel(
        id: String,
        repo: FakeLoanRepository = FakeLoanRepository(clock),
        profileRepo: ProfileRepository = profiles,
        reputationRepo: FakeReputationRepository = reputation,
    ): RequestDetailViewModel {
        val store = sessions()
        val vm = RequestDetailViewModel(
            SavedStateHandle(mapOf("loanId" to id)),
            repo,
            profileRepo,
            reputationRepo,
            LocalValueProvider(rateRepo, clock),
            FakePrefsStore(),
            store,
            ProfileGate(profileRepo),
        )
        runCurrent()
        return vm
    }

    private fun RequestDetailViewModel.detail() = (state.value.content as Loadable.Success).data

    /** An open request of 100 tokens at 10 percent for 30 days in 3 installments: the split has a remainder. */
    private fun remainderLoan(status: LoanStatus = LoanStatus.OPEN): Loan =
        FakeData.loan(
            clock, 21, FakeData.LUCIA, if (status == LoanStatus.OPEN) null else FakeData.LENDER_A, 100, 1000, 30, 3, status,
            requestedDaysAgo = 3, fundedDaysAgo = if (status == LoanStatus.OPEN) null else 2, score = 820,
        )

    @Test
    fun theEarningsAndTheInstallmentOfAnOpenRequestUseTheContractFormula() = runTest {
        val detail = viewModel("21", FakeLoanRepository(listOf(remainderLoan()))).detail()

        // 100 tokens * 1000 bps * 30 days / 3,650,000 = 821,917 base units, floored.
        assertEquals(BigInteger.valueOf(821_917), detail.earnings)
        assertEquals(BigInteger.valueOf(100_821_917), detail.totalToRepay)
        // 100,821,917 / 3 = 33,607,305 and the last installment absorbs the remainder.
        assertEquals(BigInteger.valueOf(33_607_305), detail.installmentAmount)
        assertEquals(detail.totalToRepay.subtract(TokenAmount.ofTokens(100).baseUnits), detail.earnings)
    }

    @Test
    fun aFundedLoanUsesTheStoredTotal() = runTest {
        val loan = remainderLoan(LoanStatus.ACTIVE)
        val detail = viewModel("21", FakeLoanRepository(listOf(loan))).detail()

        assertEquals(loan.totalDue, detail.totalToRepay)
        assertEquals(loan.totalDue.subtract(loan.principal), detail.earnings)
    }

    @Test
    fun theLastFiveEventsOfTheBorrowerAreRequested() = runTest {
        val vm = viewModel("1")

        assertEquals(listOf(FakeData.LUCIA to null), reputation.calls)
        val history = vm.state.value.history as RecentHistory.Items
        assertEquals(RequestDetailViewModel.HISTORY_SIZE, history.events.size)
        assertEquals(5, history.events.size)
        // Newest first, so the first row is the latest event of the borrower.
        assertTrue(history.events.zipWithNext().all { (a, b) -> a.occurredAt >= b.occurredAt })
    }

    @Test
    fun aBorrowerWithoutReputationIsTheNewBorrowerStateNotAnError() = runTest {
        val loan = FakeData.loan(clock, 30, "0x5555555555555555555555555555555555555555", null, 90, 900, 30, 2, LoanStatus.OPEN, requestedDaysAgo = 1)
        val vm = viewModel("30", FakeLoanRepository(listOf(loan)))

        assertEquals(RecentHistory.NewBorrower, vm.state.value.history)
        assertTrue(vm.state.value.content is Loadable.Success)
    }

    @Test
    fun aHistoryFailureHasItsOwnStateAndReloadingTheHistoryRecovers() = runTest {
        val repo = FakeReputationRepository(clock)
        repo.failNext = AppError.Offline
        val vm = viewModel("1", reputationRepo = repo)

        assertEquals(RecentHistory.Error(ErrorKind.Offline), vm.state.value.history)
        assertTrue("the loan stays on screen", vm.state.value.content is Loadable.Success)

        vm.reloadHistory()
        runCurrent()
        assertTrue(vm.state.value.history is RecentHistory.Items)
    }

    @Test
    fun theLoanAndTheBorrowerNameAndTheHistoryLoadInIndependentStates() = runTest {
        val gate = CompletableDeferred<String?>()
        val slowProfiles = object : ProfileRepository by FakeProfileRepository(clock) {
            override suspend fun publicName(address: String): String? = gate.await()
        }
        val vm = viewModel("1", profileRepo = slowProfiles)

        assertTrue(vm.state.value.content is Loadable.Success)
        assertTrue(vm.state.value.history is RecentHistory.Items)
        assertNull(vm.state.value.borrowerName)
        assertEquals("0x1111...1111", vm.state.value.borrowerShortAddress)

        gate.complete("Lucia Fernandez")
        runCurrent()
        assertEquals("Lucia Fernandez", vm.state.value.borrowerName)
    }

    @Test
    fun aBorrowerWithoutAPublicNameKeepsTheShortAddress() = runTest {
        val vm = viewModel("6")

        assertNull(vm.state.value.borrowerName)
        assertEquals("0x4444...4444", vm.state.value.borrowerShortAddress)
    }

    @Test
    fun anOpenRequestOfSomeoneElseOffersFund() = runTest {
        assertEquals(FundAction.Available, viewModel("1").state.value.fundAction)
    }

    @Test
    fun theOwnRequestDisablesFundWithAReason() = runTest {
        val vm = viewModel("12")

        assertEquals(LoanStatus.OPEN, vm.detail().loan.status)
        assertEquals(FundAction.OwnRequest, vm.state.value.fundAction)
    }

    @Test
    fun aRequestThatIsNoLongerOpenHasNoFundAction() = runTest {
        for (id in listOf("8", "9", "5", "13")) {
            assertEquals("loan $id", FundAction.None, viewModel(id).state.value.fundAction)
        }
    }

    @Test
    fun nothingIsOfferedWhileTheLoanLoadsOrFails() = runTest {
        val repo = FakeLoanRepository(clock)
        repo.failNext = AppError.Server
        val vm = viewModel("1", repo)

        assertEquals(Loadable.Error(ErrorKind.Server), vm.state.value.content)
        assertEquals(FundAction.None, vm.state.value.fundAction)
    }

    @Test
    fun anUnknownIdGivesTheNotFoundStateAndNoAction() = runTest {
        val vm = viewModel("999")

        assertEquals(Loadable.Error(ErrorKind.NotFound), vm.state.value.content)
        assertEquals(FundAction.None, vm.state.value.fundAction)
    }

    @Test
    fun aMalformedIdGivesTheNotFoundStateWithoutAskingForTheBorrower() = runTest {
        for (bad in listOf("abc", "-1", "1.5", "0x10", "", "9".repeat(79), "1 ")) {
            val before = reputation.calls.size
            assertEquals(bad, Loadable.Error(ErrorKind.NotFound), viewModel(bad).state.value.content)
            assertEquals(before, reputation.calls.size)
        }
    }

    @Test
    fun reloadRecoversFromAServerFailureWithFreshRates() = runTest {
        val repo = FakeLoanRepository(clock)
        repo.failNext = AppError.Server
        val vm = viewModel("1", repo)
        assertEquals(1, rateRepo.ratesCalls)

        vm.reload()
        runCurrent()

        assertTrue(vm.state.value.content is Loadable.Success)
        assertEquals(2, rateRepo.ratesCalls)
    }

    // The Fund request primary calls runProfileGate through rememberRequireProfile. The callback
    // below is the same one the screen route passes as onReady, so these cases cover the decision.

    private fun demoProfile(contact: Contact?) =
        Profile(FakeWalletGateway.DEMO_ADDRESS, "Ana Torres", contact, FakeData.EPOCH, FakeData.EPOCH)

    @Test
    fun withAMissingProfileTheFormOpensAndFundIsNotCalled() = runTest {
        val funded = mutableListOf<BigInteger>()
        var formOpened = 0
        var failed = 0

        runProfileGate(
            gate = ProfileGate(FakeProfileRepository(clock)),
            session = sessions(),
            onReady = { funded += BigInteger.ONE },
            onMissing = { formOpened += 1 },
            onError = { failed += 1 },
        )

        assertEquals(1, formOpened)
        assertTrue(funded.isEmpty())
        assertEquals(0, failed)
    }

    @Test
    fun aProfileWithoutContactStillOpensTheForm() = runTest {
        val repo = FakeProfileRepository(clock).apply { seed(demoProfile(contact = null)) }
        var formOpened = 0
        var funded = 0

        runProfileGate(ProfileGate(repo), sessions(), onReady = { funded += 1 }, onMissing = { formOpened += 1 }, onError = {})

        assertEquals(1, formOpened)
        assertEquals(0, funded)
    }

    @Test
    fun withACompleteProfileFundIsCalledOnce() = runTest {
        val repo = FakeProfileRepository(clock).apply { seed(demoProfile(Contact(ContactType.EMAIL, "ana@example.com"))) }
        var funded = 0
        var formOpened = 0

        runProfileGate(ProfileGate(repo), sessions(), onReady = { funded += 1 }, onMissing = { formOpened += 1 }, onError = {})

        assertEquals(1, funded)
        assertEquals(0, formOpened)
    }

    @Test
    fun aFailedProfileCheckIsNeverAPass() = runTest {
        val repo = FakeProfileRepository(clock).apply { failNext = AppError.Offline }
        var funded = 0
        var errors = 0

        runProfileGate(ProfileGate(repo), sessions(), onReady = { funded += 1 }, onMissing = { funded += 1 }, onError = { errors += 1 })

        assertEquals(0, funded)
        assertEquals(1, errors)
    }

    @Test
    fun withoutASessionTheGateReportsAnErrorAndNeverFunds() = runTest {
        var funded = 0
        var errors = 0

        runProfileGate(ProfileGate(FakeProfileRepository(clock)), InMemorySessionStore(), onReady = { funded += 1 }, onMissing = {}, onError = { errors += 1 })

        assertEquals(0, funded)
        assertEquals(1, errors)
    }
}
