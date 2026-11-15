package com.latifi.wallet.ui.pay

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.Erc20Abi
import com.latifi.wallet.data.chain.LoanBookAbi
import com.latifi.wallet.data.chain.RevertDecoder
import com.latifi.wallet.data.fake.FakeChainReadRepository
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.model.InstallmentSchedule
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.tx.FailReason
import com.latifi.wallet.domain.tx.IndexerLagWaiter
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.TxHarness
import com.latifi.wallet.ui.common.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PayViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)

    /** Loan 31 is two installments of a 100 tUSD loan funded 20 days ago for 30 days, so the first is 5 days late. */
    private val lateLoan = FakeData.loan(
        clock, 31, FakeData.DEMO_ADDRESS, FakeData.LENDER_A, 100, 1000, 30, 2, LoanStatus.ACTIVE,
        paid = 0, requestedDaysAgo = 40, fundedDaysAgo = 20,
    )

    private class Setup(
        val vm: PayViewModel,
        val h: TxHarness,
        val chain: FakeChainReadRepository,
        val loans: FakeLoanRepository,
    )

    private fun TestScope.setup(
        id: String = "11",
        script: FakeWalletScript = FakeWalletScript.AutoApprove(delayMillis = 0),
        allowance: BigInteger = BigInteger.ZERO,
    ): Setup {
        val loans = FakeLoanRepository(FakeData.loans(clock) + lateLoan)
        val chain = FakeChainReadRepository { loans.loans }
        chain.setAllowance(TxHarness.ADDRESS, Deployment.LOANBOOK_ADDRESS, allowance)
        val h = TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }
        val sessions = InMemorySessionStore().also {
            runBlocking { it.save(Session(TxHarness.ADDRESS, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        }
        val vm = PayViewModel(
            savedState = SavedStateHandle(mapOf("loanId" to id)),
            loans = loans,
            chain = chain,
            coordinator = h.coordinator,
            wallet = h.wallet,
            rates = LocalValueProvider(FakeExchangeRateRepository(clock), clock),
            exchange = FakeExchangeRateRepository(clock),
            waiter = IndexerLagWaiter(),
            clock = clock,
            sessions = sessions,
            prefs = FakePrefsStore(),
        )
        runCurrent()
        return Setup(vm, h, chain, loans)
    }

    private fun Setup.data() = (vm.state.value.content as Loadable.Success).data

    private fun Setup.sent() = h.wallet.sentRequests.map { it.second }.filterIsInstance<WalletRequest.SendTransaction>()

    @Test
    fun theAmountIsTheUnpaidAmountOfTheCurrentInstallmentAndNotTheTotalDue() = runTest {
        val s = setup()
        val loan = s.loans.loans.single { it.loanId == BigInteger.valueOf(11) }

        assertEquals(InstallmentSchedule.remainingOnCurrent(loan), s.data().unpaid.baseUnits)
        assertNotEquals(loan.totalDue, s.data().unpaid.baseUnits)
        assertEquals(2, s.data().index)
    }

    @Test
    fun withEnoughAllowanceThereIsOneRepayStep() = runTest {
        val s = setup(allowance = BigInteger.TEN.pow(12))
        val unpaid = s.data().unpaid.baseUnits

        s.vm.onConfirm()
        runCurrent()

        assertEquals(1, s.sent().size)
        assertEquals(LoanBookAbi.repay(BigInteger.valueOf(11), unpaid), s.sent().single().data)
        assertEquals(Deployment.LOANBOOK_ADDRESS, s.sent().single().to)
        assertEquals(1, s.h.coordinator.context.value?.stepCount)
    }

    @Test
    fun withAShortAllowanceItApprovesExactlyTheAmountThenRepays() = runTest {
        val s = setup(allowance = BigInteger.ZERO)
        val unpaid = s.data().unpaid.baseUnits

        s.vm.onConfirm()
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(1, s.sent().size)
        assertEquals(Deployment.STABLECOIN_ADDRESS, s.sent()[0].to)
        assertEquals(Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, unpaid), s.sent()[0].data)
        assertEquals(2, s.h.coordinator.context.value?.stepCount)
        assertNotNullAllowText(s)
        assertTrue(s.h.coordinator.state.value is TxState.Confirmed)

        // The Confirmed button of step 1 starts step 2.
        s.h.coordinator.context.value!!.confirmedPrimary!!.onClick()
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(2, s.sent().size)
        assertEquals(LoanBookAbi.repay(BigInteger.valueOf(11), unpaid), s.sent()[1].data)
        assertEquals(2, s.h.coordinator.context.value?.stepIndex)
    }

    private fun assertNotNullAllowText(s: Setup) {
        assertTrue(s.h.coordinator.context.value?.allowAmountText?.isNotEmpty() == true)
    }

    @Test
    fun aLateInstallmentIsFlaggedAndStillPayable() = runTest {
        val s = setup(id = "31")

        assertTrue(s.data().late)
        assertFalse(s.data().blocked)
        assertEquals(1, s.data().index)
    }

    @Test
    fun anInstallmentNotYetDueIsNotLate() = runTest {
        assertFalse(setup(id = "11").data().late)
    }

    @Test
    fun aDefaultedLoanHasNoPrimaryAndSendsNothing() = runTest {
        val s = setup(id = "5")

        assertTrue(s.data().blocked)
        s.vm.onConfirm()
        runCurrent()

        assertTrue(s.h.wallet.sentRequests.isEmpty())
    }

    @Test
    fun anotherPersonsLoanIsNotFoundAndNeverReachesTheWallet() = runTest {
        val s = setup(id = "8")

        assertTrue(s.vm.state.value.content is Loadable.Error)
        s.vm.onConfirm()
        runCurrent()
        assertTrue(s.h.wallet.sentRequests.isEmpty())
    }

    @Test
    fun anOverpaymentRevertReloadsTheInstallmentAndDoesNotResend() = runTest {
        val s = setup(allowance = BigInteger.TEN.pow(12))
        s.h.rpc.revertWith(RevertDecoder.OVERPAYMENT + "0".repeat(128))
        val readsBefore = s.chain.reads.count { it.first == FakeChainReadRepository.Read.NextInstallment }

        s.vm.onConfirm()
        runCurrent()

        assertEquals(TxState.Failed(FailReason.Reverted, refreshInstallment = true), s.h.coordinator.state.value)
        assertEquals(readsBefore + 1, s.chain.reads.count { it.first == FakeChainReadRepository.Read.NextInstallment })
        assertTrue("nothing was sent by itself", s.h.wallet.sentRequests.isEmpty())
    }

    @Test
    fun theLastStepPrimaryWaitsForTheApiThenOpensLoans() = runTest {
        val s = setup(allowance = BigInteger.TEN.pow(12))
        s.vm.onConfirm()
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(s.h.coordinator.state.value is TxState.Confirmed)

        s.vm.effects.test {
            s.h.coordinator.context.value!!.confirmedPrimary!!.onClick()
            runCurrent()
            assertTrue(s.vm.state.value.waitingForIndexer)
            // The API shows the payment 4 seconds later: the amount repaid grows.
            advanceTimeBy(4_000)
            s.loans.loans = s.loans.loans.map { if (it.loanId == BigInteger.valueOf(11)) it.copy(amountRepaid = it.amountRepaid.add(BigInteger.ONE)) else it }
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(PayEffect.OpenLoans, awaitItem())
        }
        assertEquals(TokenAmount::class, s.data().unpaid::class)
    }
}
