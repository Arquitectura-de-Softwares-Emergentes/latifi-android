package com.latifi.wallet.ui.tx

import app.cash.turbine.test
import com.latifi.wallet.R
import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.domain.tx.FailReason
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxPrimary
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.TxHarness
import com.latifi.wallet.ui.nav.Overlays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TxStatusViewModelTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()

    private fun TestScope.harness(script: FakeWalletScript = FakeWalletScript.NeverAnswer) =
        TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }

    private fun TxHarness.viewModel() = TxStatusViewModel(coordinator, wallet, overlays)

    private val hash1 = TxHarness.hashOf(1)

    @Test
    fun openWalletBringsTheWalletForwardAndNeverSendsASecondRequest() = runTest {
        val h = harness()
        val vm = h.viewModel()
        h.begin()
        runCurrent()

        vm.onEvent(TxStatusEvent.OpenWallet)
        vm.onEvent(TxStatusEvent.OpenWallet)
        runCurrent()

        assertEquals(2, h.wallet.walletAppOpened)
        assertEquals("only the first request was ever sent", 1, h.wallet.sentRequests.size)
        assertTrue(h.coordinator.state.value is TxState.Pending)
    }

    @Test
    fun backInPendingMovesFocusToCancelAndKeepsTheSheetOpen() = runTest {
        val h = harness()
        val vm = h.viewModel()
        h.begin()
        runCurrent()

        vm.effects.test {
            vm.onEvent(TxStatusEvent.Back)
            assertEquals(TxStatusEffect.FocusCancel, awaitItem())
        }
        assertEquals(Overlays.TX_STATUS, h.overlays.current.value)
        assertTrue(h.coordinator.state.value is TxState.Pending)
    }

    @Test
    fun backInConfirmingActsAsBackToHomeAndTheCoordinatorKeepsTracking() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        h.rpc.script(hash1, FakeReceiptScript(nullPolls = 2))
        var wentHome = 0
        val vm = h.viewModel()
        h.begin(context = TxContext(TxAction.Payment, onBackToHome = { wentHome += 1 }))
        runCurrent()
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)

        vm.onEvent(TxStatusEvent.Back)

        assertNull("the sheet is hidden", h.overlays.current.value)
        assertEquals(1, wentHome)
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)

        advanceTimeBy(6_000)
        runCurrent()
        assertTrue("the result is still tracked", h.coordinator.state.value is TxState.Confirmed)
        assertEquals("and the sheet comes back to show it", Overlays.TX_STATUS, h.overlays.current.value)
    }

    @Test
    fun backOnAFinishedTransactionClosesTheSheet() = runTest {
        val h = harness(FakeWalletScript.Reject(delayMillis = 0))
        val vm = h.viewModel()
        h.begin()
        runCurrent()
        assertEquals(TxState.Failed(FailReason.Declined), h.coordinator.state.value)

        vm.onEvent(TxStatusEvent.Back)

        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertNull(h.overlays.current.value)
    }

    @Test
    fun theConfirmedPrimaryRunsTheFlowActionAndClosesTheSheetWhenNothingStarted() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        var ran = 0
        val vm = h.viewModel()
        h.begin(context = TxContext(TxAction.Payment, confirmedPrimary = TxPrimary(R.string.back_to_home) { ran += 1 }))
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Confirmed)

        vm.onEvent(TxStatusEvent.ConfirmedPrimary)

        assertEquals(1, ran)
        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertNull(h.overlays.current.value)
    }

    @Test
    fun theConfirmedPrimaryThatStartsTheSecondStepKeepsTheSheetOpen() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        val vm = h.viewModel()
        val second = TxContext(TxAction.Payment, stepIndex = 2, stepCount = 2)
        val first = TxContext(
            TxAction.Payment, stepIndex = 1, stepCount = 2,
            confirmedPrimary = TxPrimary(R.string.back_to_home) { h.coordinator.begin(second, TxHarness.repayStep(), TxHarness.ADDRESS) },
        )
        h.begin(context = first)
        advanceTimeBy(6_000)
        runCurrent()

        vm.onEvent(TxStatusEvent.ConfirmedPrimary)

        assertTrue(h.coordinator.state.value is TxState.Pending)
        assertEquals(2, h.coordinator.context.value?.stepIndex)
        assertEquals(Overlays.TX_STATUS, h.overlays.current.value)
    }

    @Test
    fun withNoFlowPrimaryTheConfirmedButtonIsBackToHome() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        var wentHome = 0
        val vm = h.viewModel()
        h.begin(context = TxContext(TxAction.Payment, onBackToHome = { wentHome += 1 }))
        advanceTimeBy(6_000)
        runCurrent()

        vm.onEvent(TxStatusEvent.ConfirmedPrimary)

        assertEquals(1, wentHome)
        assertEquals(TxState.Idle, h.coordinator.state.value)
    }

    @Test
    fun copyReceiptAndViewReceiptUseOnlyAValidatedReference() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        val vm = h.viewModel()
        h.begin()
        advanceTimeBy(6_000)
        runCurrent()

        vm.effects.test {
            vm.onEvent(TxStatusEvent.CopyReceipt)
            assertEquals(TxStatusEffect.CopyText(hash1, R.string.receipt_copied), awaitItem())
            vm.onEvent(TxStatusEvent.ViewReceipt)
            assertEquals(TxStatusEffect.OpenUrl("https://amoy.polygonscan.com/tx/$hash1"), awaitItem())
        }
    }

    @Test
    fun noReceiptEffectExistsBeforeAHashIsConfirmed() = runTest {
        val h = harness()
        val vm = h.viewModel()
        h.begin()
        runCurrent()

        vm.effects.test {
            vm.onEvent(TxStatusEvent.CopyReceipt)
            vm.onEvent(TxStatusEvent.ViewReceipt)
            expectNoEvents()
        }
    }

    @Test
    fun copyWalletAddressCopiesTheConnectedAddress() = runTest {
        val h = harness()
        val vm = h.viewModel()

        vm.effects.test {
            vm.onEvent(TxStatusEvent.CopyWalletAddress)
            assertEquals(TxStatusEffect.CopyText(TxHarness.ADDRESS, R.string.profile_address_copied), awaitItem())
        }
    }

    @Test
    fun getTestCoinsClosesTheTransactionAndOpensTheCoinsSheet() = runTest {
        val h = harness(FakeWalletScript.Reject(delayMillis = 0))
        val vm = h.viewModel()
        h.begin()
        runCurrent()

        vm.onEvent(TxStatusEvent.GetTestCoins)

        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertEquals(Overlays.COINS, h.overlays.current.value)
    }

    @Test
    fun backToRequestsClosesTheSheetAndCallsTheFlow() = runTest {
        val h = harness(FakeWalletScript.Reject(delayMillis = 0))
        var back = 0
        val vm = h.viewModel()
        h.begin(context = TxContext(TxAction.Funding, onBackToRequests = { back += 1 }))
        runCurrent()

        vm.onEvent(TxStatusEvent.BackToRequests)

        assertEquals(1, back)
        assertEquals(TxState.Idle, h.coordinator.state.value)
    }

    @Test
    fun theSheetCannotBeDismissedByGestureWhileInFlight() {
        assertFalse(canDismissByGesture(TxState.Pending(0)))
        assertFalse(canDismissByGesture(TxState.PendingTimeout))
        assertFalse(canDismissByGesture(TxState.Confirming("h")))
        assertFalse(canDismissByGesture(TxState.ConfirmingSlow("h")))
        assertFalse(canDismissByGesture(TxState.Reconciling))
        assertTrue(canDismissByGesture(TxState.Confirmed("h", null)))
        assertTrue(canDismissByGesture(TxState.Failed(FailReason.Network)))
        assertTrue(canDismissByGesture(TxState.Idle))
    }
}
