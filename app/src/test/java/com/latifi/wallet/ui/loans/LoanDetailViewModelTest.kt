package com.latifi.wallet.ui.loans

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.model.InstallmentState
import com.latifi.wallet.domain.model.LoanStatus
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
import java.math.BigInteger
import java.time.Clock
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LoanDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val repo = FakeLoanRepository(clock)
    private val rateRepo = FakeExchangeRateRepository(clock)

    private fun TestScope.viewModel(
        id: String,
        profiles: ProfileRepository = FakeProfileRepository(clock),
    ): LoanDetailViewModel {
        val vm = LoanDetailViewModel(
            SavedStateHandle(mapOf("loanId" to id)),
            repo,
            profiles,
            LocalValueProvider(rateRepo, clock),
            FakePrefsStore(),
        )
        runCurrent()
        return vm
    }

    private fun LoanDetailViewModel.detail() = (state.value.content as Loadable.Success).data

    @Test
    fun theActionDependsOnTheStatus() = runTest {
        assertEquals(LoanAction.Pay, viewModel("11").detail().action)
        assertEquals(LoanAction.WaitingForLender, viewModel("12").detail().action)
        assertEquals(LoanAction.Repaid, viewModel("7").detail().action)
        assertEquals(LoanAction.Blocked, viewModel("5").detail().action)
    }

    @Test
    fun aPartiallyPaidLoanHasOnePaidOneDueAndOneUpcomingInstallment() = runTest {
        val detail = viewModel("11").detail()

        assertEquals(
            listOf(InstallmentState.PAID, InstallmentState.DUE, InstallmentState.UPCOMING),
            detail.schedule.map { it.state },
        )
        assertEquals(3, detail.schedule.size)
        assertEquals(detail.loan.totalDue, detail.totalToRepay)
        assertEquals(detail.schedule.first().amount, detail.installmentAmount)
    }

    @Test
    fun anOverdueLoanKeepsItsFirstUnpaidInstallmentDue() = runTest {
        val detail = viewModel("5").detail()

        assertEquals(InstallmentState.DUE, detail.schedule.first().state)
        assertEquals(LoanAction.Blocked, detail.action)
    }

    @Test
    fun anOpenRequestShowsTheFormulaTotalBecauseTheApiReportsZero() = runTest {
        val detail = viewModel("12").detail()

        assertEquals(BigInteger.ZERO, detail.loan.totalDue)
        assertEquals(LoanMath.totalDue(TokenAmount.ofTokens(120).baseUnits, 800, 45), detail.totalToRepay)
        assertTrue(detail.schedule.all { it.state == InstallmentState.UPCOMING })
    }

    @Test
    fun theLenderShowsAShortAddressFirstAndTheNameWhenItArrives() = runTest {
        val gate = CompletableDeferred<String?>()
        val slowProfiles = object : ProfileRepository by FakeProfileRepository(clock) {
            override suspend fun publicName(address: String): String? = gate.await()
        }
        val vm = viewModel("11", slowProfiles)

        assertEquals("0xaaaa...aaaa", vm.state.value.lenderShortAddress)
        assertNull(vm.state.value.lenderName)
        assertTrue(vm.state.value.content is Loadable.Success)

        gate.complete("Rosa Medina")
        runCurrent()
        assertEquals("Rosa Medina", vm.state.value.lenderName)
    }

    @Test
    fun aLenderWithoutAPublicNameKeepsTheShortAddress() = runTest {
        val vm = viewModel("11")

        assertNull(vm.state.value.lenderName)
        assertEquals("0xaaaa...aaaa", vm.state.value.lenderShortAddress)
    }

    @Test
    fun anOpenRequestHasNoLender() = runTest {
        val vm = viewModel("12")

        assertNull(vm.state.value.lenderShortAddress)
        assertNull(vm.state.value.lenderName)
    }

    @Test
    fun anUnknownIdGivesTheNotFoundState() = runTest {
        assertEquals(Loadable.Error(ErrorKind.NotFound), viewModel("999").state.value.content)
    }

    @Test
    fun aMalformedIdGivesTheNotFoundStateWithoutAskingTheRepository() = runTest {
        for (bad in listOf("abc", "-1", "1.5", "0x10", "", "9".repeat(79), "1 ")) {
            assertEquals(bad, Loadable.Error(ErrorKind.NotFound), viewModel(bad).state.value.content)
        }
        assertEquals(BigInteger("9".repeat(78)), LoanDetailViewModel.parseLoanId("9".repeat(78)))
    }

    @Test
    fun aServerFailureGivesTheErrorStateAndReloadRecovers() = runTest {
        repo.failNext = AppError.Server
        val vm = viewModel("11")
        assertEquals(Loadable.Error(ErrorKind.Server), vm.state.value.content)

        vm.reload()
        runCurrent()
        assertTrue(vm.state.value.content is Loadable.Success)
        assertEquals(LoanStatus.ACTIVE, vm.detail().loan.status)
    }

    @Test
    fun reloadAlsoAsksForFreshRates() = runTest {
        val vm = viewModel("11")
        assertEquals(1, rateRepo.ratesCalls)

        vm.reload()
        runCurrent()

        assertEquals(2, rateRepo.ratesCalls)
    }
}
