package com.latifi.wallet.ui.fund

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.Erc20Abi
import com.latifi.wallet.data.chain.LoanBookAbi
import com.latifi.wallet.data.fake.FakeChainReadRepository
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FundViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)

    private class Setup(val vm: FundViewModel, val h: TxHarness, val loans: FakeLoanRepository)

    /** The signed-in lender is the demo address. Loan 1 is Lucia's open request of 500 tUSD, loan 12 is the demo person's own. */
    private fun TestScope.setup(
        id: String = "1",
        allowance: BigInteger = BigInteger.ZERO,
        script: FakeWalletScript = FakeWalletScript.AutoApprove(delayMillis = 0),
    ): Setup {
        val loans = FakeLoanRepository(clock)
        val chain = FakeChainReadRepository { loans.loans }
        chain.setAllowance(TxHarness.ADDRESS, Deployment.LOANBOOK_ADDRESS, allowance)
        val h = TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }
        val sessions = InMemorySessionStore().also {
            runBlocking { it.save(Session(TxHarness.ADDRESS, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        }
        val vm = FundViewModel(
            savedState = SavedStateHandle(mapOf("loanId" to id)),
            loans = loans,
            chain = chain,
            coordinator = h.coordinator,
            wallet = h.wallet,
            rates = LocalValueProvider(FakeExchangeRateRepository(clock), clock),
            waiter = IndexerLagWaiter(),
            sessions = sessions,
            prefs = FakePrefsStore(),
        )
        runCurrent()
        return Setup(vm, h, loans)
    }

    private fun Setup.sent() = h.wallet.sentRequests.map { it.second }.filterIsInstance<WalletRequest.SendTransaction>()

    private fun Setup.principal() = (vm.state.value.content as Loadable.Success).data.detail.loan.principal

    @Test
    fun withEnoughAllowanceThereIsOneFundStep() = runTest {
        val s = setup(allowance = BigInteger.TEN.pow(12))

        s.vm.onConfirm()
        runCurrent()

        assertEquals(1, s.sent().size)
        assertEquals(LoanBookAbi.fund(BigInteger.ONE), s.sent().single().data)
        assertEquals(1, s.h.coordinator.context.value?.stepCount)
    }

    @Test
    fun withAShortAllowanceTheApproveAmountEqualsThePrincipalThenItFunds() = runTest {
        val s = setup(allowance = BigInteger.ZERO)
        val principal = s.principal()

        s.vm.onConfirm()
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, principal), s.sent().single().data)
        assertEquals(Deployment.STABLECOIN_ADDRESS, s.sent().single().to)
        assertTrue(s.h.coordinator.state.value is TxState.Confirmed)

        s.h.coordinator.context.value!!.confirmedPrimary!!.onClick()
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(2, s.sent().size)
        assertEquals(LoanBookAbi.fund(BigInteger.ONE), s.sent()[1].data)
    }

    @Test
    fun aNotOpenFailureOffersBackToRequests() = runTest {
        val s = setup(allowance = BigInteger.TEN.pow(12))
        // The preflight says the request is gone (LoanNotOpen).
        s.h.rpc.revertWith(com.latifi.wallet.data.chain.RevertDecoder.LOAN_NOT_OPEN + "0".repeat(64))

        s.vm.onConfirm()
        runCurrent()
        assertEquals(TxState.Failed(com.latifi.wallet.domain.tx.FailReason.NotOpen), s.h.coordinator.state.value)

        s.vm.effects.test {
            s.h.coordinator.context.value!!.onBackToRequests!!.invoke()
            assertEquals(FundEffect.OpenRequests, awaitItem())
        }
        assertEquals(com.latifi.wallet.domain.tx.FailureActions.primaryFor(com.latifi.wallet.domain.tx.FailReason.NotOpen), com.latifi.wallet.domain.tx.FailurePrimary.BackToRequests)
    }

    @Test
    fun aRequestThatIsNotOpenShowsTheNotOpenStateAndSendsNothing() = runTest {
        val s = setup(id = "7")

        assertTrue(s.vm.state.value.notOpen)
        s.vm.onConfirm()
        runCurrent()
        assertTrue(s.h.wallet.sentRequests.isEmpty())
    }

    @Test
    fun anOwnRequestIsRefused() = runTest {
        val s = setup(id = "12", allowance = BigInteger.TEN.pow(12))

        assertTrue(s.vm.state.value.ownRequest)
        s.vm.onConfirm()
        runCurrent()
        assertTrue(s.h.wallet.sentRequests.isEmpty())
    }

    @Test
    fun theLastStepPrimaryWaitsForTheActiveLoanThenOpensPortfolio() = runTest {
        val s = setup(allowance = BigInteger.TEN.pow(12))
        s.vm.onConfirm()
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(s.h.coordinator.state.value is TxState.Confirmed)

        s.vm.effects.test {
            s.h.coordinator.context.value!!.confirmedPrimary!!.onClick()
            runCurrent()
            assertTrue(s.vm.state.value.waitingForIndexer)
            expectNoEvents()
            s.loans.loans = s.loans.loans.map {
                if (it.loanId == BigInteger.ONE) it.copy(status = com.latifi.wallet.domain.model.LoanStatus.ACTIVE, lender = TxHarness.ADDRESS) else it
            }
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(FundEffect.OpenPortfolio, awaitItem())
        }
        assertFalse(s.vm.state.value.waitingForIndexer)
    }
}
