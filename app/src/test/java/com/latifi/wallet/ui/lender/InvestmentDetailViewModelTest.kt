package com.latifi.wallet.ui.lender

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.model.InstallmentState
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.UiLoanStatus
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val rateRepo = FakeExchangeRateRepository(clock)

    private fun TestScope.viewModel(
        id: String,
        repo: FakeLoanRepository = FakeLoanRepository(clock),
        profiles: ProfileRepository = FakeProfileRepository(clock),
    ): InvestmentDetailViewModel {
        val vm = InvestmentDetailViewModel(
            SavedStateHandle(mapOf("loanId" to id)),
            repo,
            profiles,
            LocalValueProvider(rateRepo, clock),
            FakePrefsStore(),
        )
        runCurrent()
        return vm
    }

    private fun InvestmentDetailViewModel.detail() = (state.value.content as Loadable.Success).data

    private fun lent(status: LoanStatus, paid: Int, installments: Int = 3, fundedDaysAgo: Long = 12): Loan =
        FakeData.loan(
            clock, 40, FakeData.LUCIA, FakeData.DEMO_ADDRESS, 150, 1000, 30, installments, status,
            paid = paid, requestedDaysAgo = fundedDaysAgo + 1, fundedDaysAgo = fundedDaysAgo,
        )

    @Test
    fun withNothingPaidTheFirstInstallmentIsDueAndTheRestUpcoming() = runTest {
        val detail = viewModel("40", FakeLoanRepository(listOf(lent(LoanStatus.ACTIVE, paid = 0)))).detail()

        assertEquals(
            listOf(InstallmentState.DUE, InstallmentState.UPCOMING, InstallmentState.UPCOMING),
            detail.schedule.map { it.state },
        )
    }

    @Test
    fun withOneInstallmentPaidTheListReadsPaidDueUpcoming() = runTest {
        val detail = viewModel("40", FakeLoanRepository(listOf(lent(LoanStatus.ACTIVE, paid = 1)))).detail()

        assertEquals(
            listOf(InstallmentState.PAID, InstallmentState.DUE, InstallmentState.UPCOMING),
            detail.schedule.map { it.state },
        )
        assertEquals(3, detail.schedule.size)
    }

    @Test
    fun withEveryInstallmentPaidTheWholeListIsPaidAndNoneIsDue() = runTest {
        val detail = viewModel("40", FakeLoanRepository(listOf(lent(LoanStatus.REPAID, paid = 3)))).detail()

        assertTrue(detail.schedule.all { it.state == InstallmentState.PAID })
        assertEquals(UiLoanStatus.Paid, detail.loan.uiStatus)
    }

    @Test
    fun theExpectedReturnIsTheStoredTotalAndTheScheduleSumsToIt() = runTest {
        val loan = lent(LoanStatus.ACTIVE, paid = 1)
        val detail = viewModel("40", FakeLoanRepository(listOf(loan))).detail()

        assertEquals(loan.totalDue, detail.expectedReturn)
        assertEquals(TokenAmount(LoanMath.totalDue(loan.principal, 1000, 30)), TokenAmount(detail.expectedReturn))
        assertEquals(detail.expectedReturn, detail.schedule.fold(java.math.BigInteger.ZERO) { sum, row -> sum.add(row.amount) })
    }

    @Test
    fun aDefaultedLoanShowsOverdueBecauseTheApiSaysSo() = runTest {
        val detail = viewModel("40", FakeLoanRepository(listOf(lent(LoanStatus.DEFAULTED, paid = 0, fundedDaysAgo = 50)))).detail()

        assertEquals(UiLoanStatus.Overdue, detail.loan.uiStatus)
    }

    @Test
    fun anActiveLoanWithAPastDueDateIsNeverShownAsOverdue() = runTest {
        // Funded 50 days ago with a 30 day term: the next due date passed weeks ago. The API still says ACTIVE.
        val loan = lent(LoanStatus.ACTIVE, paid = 0, fundedDaysAgo = 50)
        val detail = viewModel("40", FakeLoanRepository(listOf(loan))).detail()

        assertTrue(detail.loan.nextDueDate!!.isBefore(FakeData.EPOCH.minusSeconds(7L * 24 * 3600)))
        assertEquals(UiLoanStatus.Pending, detail.loan.uiStatus)
    }

    @Test
    fun theBorrowerShowsAShortAddressFirstAndTheNameWhenItArrives() = runTest {
        val gate = CompletableDeferred<String?>()
        val slowProfiles = object : ProfileRepository by FakeProfileRepository(clock) {
            override suspend fun publicName(address: String): String? = gate.await()
        }
        val vm = viewModel("8", profiles = slowProfiles)

        assertEquals("0x1111...1111", vm.state.value.borrowerShortAddress)
        assertNull(vm.state.value.borrowerName)
        assertTrue(vm.state.value.content is Loadable.Success)

        gate.complete("Lucia Fernandez")
        runCurrent()
        assertEquals("Lucia Fernandez", vm.state.value.borrowerName)
    }

    @Test
    fun aBorrowerWithoutAPublicNameKeepsTheShortAddress() = runTest {
        val vm = viewModel("14")

        assertNull(vm.state.value.borrowerName)
        assertEquals("0x4444...4444", vm.state.value.borrowerShortAddress)
    }

    @Test
    fun anUnknownIdGivesTheNotFoundState() = runTest {
        assertEquals(Loadable.Error(ErrorKind.NotFound), viewModel("999").state.value.content)
    }

    @Test
    fun aMalformedIdGivesTheNotFoundState() = runTest {
        for (bad in listOf("abc", "-1", "1.5", "0x10", "", "9".repeat(79), "1 ")) {
            assertEquals(bad, Loadable.Error(ErrorKind.NotFound), viewModel(bad).state.value.content)
        }
    }

    @Test
    fun aServerFailureGivesTheErrorStateAndReloadRecoversWithFreshRates() = runTest {
        val repo = FakeLoanRepository(clock)
        repo.failNext = AppError.Server
        val vm = viewModel("8", repo)
        assertEquals(Loadable.Error(ErrorKind.Server), vm.state.value.content)
        assertEquals(1, rateRepo.ratesCalls)

        vm.reload()
        runCurrent()

        assertTrue(vm.state.value.content is Loadable.Success)
        assertEquals(2, rateRepo.ratesCalls)
    }
}
