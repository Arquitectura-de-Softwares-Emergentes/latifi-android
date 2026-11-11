package com.latifi.wallet.ui.coins

import app.cash.turbine.test
import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.Erc20Abi
import com.latifi.wallet.data.fake.FakeChainReadRepository
import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.domain.tx.BalanceRefresh
import com.latifi.wallet.domain.tx.FailReason
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.TxHarness
import com.latifi.wallet.ui.nav.Overlays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CoinsViewModelTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val balances = BalanceRefresh()
    private val chain = FakeChainReadRepository()

    private fun TestScope.harness(script: FakeWalletScript = FakeWalletScript.AutoApprove(delayMillis = 0)) =
        TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }

    private fun TxHarness.viewModel() = CoinsViewModel(coordinator, wallet, chain, overlays, balances)

    @Test
    fun confirmBeginsOneMintStepWithTheExactCalldata() = runTest {
        val h = harness(FakeWalletScript.NeverAnswer)
        val vm = h.viewModel()
        h.overlays.show(Overlays.COINS)

        vm.confirm()
        runCurrent()

        val sent = h.wallet.sentRequests.map { it.second }.filterIsInstance<WalletRequest.SendTransaction>()
        assertEquals(1, sent.size)
        assertEquals(Deployment.STABLECOIN_ADDRESS, sent.single().to)
        assertEquals(Erc20Abi.mint(BigInteger.valueOf(1_000_000_000)), sent.single().data)
        assertEquals(TxAction.Coins, h.coordinator.context.value?.action)
        assertEquals("the coins sheet stepped aside for the status sheet", Overlays.TX_STATUS, h.overlays.current.value)
    }

    @Test
    fun doubleTapsStartOneTransaction() = runTest {
        val h = harness(FakeWalletScript.NeverAnswer)
        val vm = h.viewModel()

        vm.confirm()
        vm.confirm()
        runCurrent()
        vm.confirm()
        runCurrent()

        assertEquals(1, h.wallet.sentRequests.size)
    }

    @Test
    fun aConfirmedStateRefreshesTheBalanceOnceThroughThePrimary() = runTest {
        val h = harness()
        val vm = h.viewModel()
        vm.confirm()
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Confirmed)

        balances.ticks.test {
            h.coordinator.context.value!!.confirmedPrimary!!.onClick()
            assertEquals(Unit, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun aRevertedMintShowsTheReasonOfTheCoordinator() = runTest {
        val h = harness()
        h.rpc.script(TxHarness.hashOf(1), FakeReceiptScript(status = "0x0"))
        val vm = h.viewModel()

        vm.confirm()
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(TxState.Failed(FailReason.Reverted), h.coordinator.state.value)
    }

    @Test
    fun aWalletWithNoNetworkFeeFailsWithTheFeeReason() = runTest {
        val h = harness()
        h.rpc.setBalance(TxHarness.ADDRESS, BigInteger.ZERO)
        val vm = h.viewModel()

        vm.confirm()
        runCurrent()

        assertEquals(TxState.Failed(FailReason.Fee), h.coordinator.state.value)
        assertTrue("nothing reached the wallet", h.wallet.sentRequests.isEmpty())
    }

    @Test
    fun withNoWalletNothingStartsAndTheSheetSaysSo() = runTest {
        val h = harness()
        h.wallet.setState(com.latifi.wallet.domain.wallet.WalletState.Disconnected)
        val vm = h.viewModel()

        vm.confirm()
        runCurrent()

        assertTrue(vm.state.value.needsWallet)
        assertTrue(h.wallet.sentRequests.isEmpty())
        assertNull(h.coordinator.context.value)
    }
}
