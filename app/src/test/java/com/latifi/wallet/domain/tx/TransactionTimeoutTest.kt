package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletState
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
import org.junit.Test

/** D-04 on virtual time: 15 seconds to the timeout panel, no limit on waiting, and a delay is never a failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionTimeoutTest {
    private fun TestScope.harness(script: FakeWalletScript = FakeWalletScript.NeverAnswer): TxHarness =
        TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }

    private val hash1 = TxHarness.hashOf(1)

    private suspend fun TestScope.passTime(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun stillPendingAt14999MillisAndTimedOutAtFifteenSeconds() = runTest {
        val h = harness()
        h.begin()
        runCurrent()

        passTime(14_999)
        assertTrue(h.coordinator.state.value is TxState.Pending)

        passTime(1)
        assertEquals(TxState.PendingTimeout, h.coordinator.state.value)
    }

    @Test
    fun keepWaitingRestartsTheFifteenSecondsFiveTimesInARow() = runTest {
        val h = harness()
        h.begin()
        runCurrent()

        repeat(5) { round ->
            passTime(15_000)
            assertEquals("round $round", TxState.PendingTimeout, h.coordinator.state.value)

            h.coordinator.keepWaiting()
            assertTrue("round $round is Pending again", h.coordinator.state.value is TxState.Pending)
            passTime(14_999)
            assertTrue("round $round still waiting", h.coordinator.state.value is TxState.Pending)
        }
        passTime(1)
        assertEquals(TxState.PendingTimeout, h.coordinator.state.value)
        assertEquals("keeping waiting never sends a second request", 1, h.wallet.sentRequests.size)
    }

    @Test
    fun anAnswerAfterTheTimeoutIsStillAccepted() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        passTime(15_000)
        assertEquals(TxState.PendingTimeout, h.coordinator.state.value)

        h.wallet.respond(1, WalletOutcome.TransactionHash(hash1))
        runCurrent()

        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)
    }

    @Test
    fun reconnectStaysInTheTimeoutUntilTheWalletIsConnectedThenSendsTheStepAgainWithANewRequestId() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        passTime(15_000)
        assertEquals(TxState.PendingTimeout, h.coordinator.state.value)

        h.coordinator.reconnect()
        runCurrent()
        assertEquals(1, h.wallet.pickerOpened)
        assertEquals("the person still sees the timeout panel", TxState.PendingTimeout, h.coordinator.state.value)
        assertEquals(1, h.wallet.sentRequests.size)

        // The fake wallet needs 600 ms to connect.
        passTime(600)
        assertEquals(TxState.Pending(15_600), h.coordinator.state.value)
        assertEquals(listOf(1L, 2L), h.wallet.sentRequests.map { it.first })
        val first = h.wallet.sentRequests[0].second as WalletRequest.SendTransaction
        val second = h.wallet.sentRequests[1].second as WalletRequest.SendTransaction
        assertEquals("the same step", first.copy(from = second.from), second)
        assertEquals(2L, h.recovery.record?.requestId)

        // The answer of the first request is for a request the app no longer follows.
        h.wallet.respond(1, WalletOutcome.TransactionHash(hash1))
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Pending)
        h.wallet.respond(2, WalletOutcome.TransactionHash(TxHarness.hashOf(2)))
        runCurrent()
        assertEquals(TxState.Confirming(TxHarness.hashOf(2)), h.coordinator.state.value)
    }

    @Test
    fun reconnectUsesTheAddressTheWalletConnectedWith() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        passTime(15_000)

        h.coordinator.reconnect()
        passTime(600)

        val first = h.wallet.sentRequests[0].second as WalletRequest.SendTransaction
        val second = h.wallet.sentRequests[1].second as WalletRequest.SendTransaction
        assertEquals(TxHarness.ADDRESS, first.from)
        assertEquals(TxHarness.RECONNECT_ADDRESS, second.from)
        assertEquals(WalletState.Connected(TxHarness.RECONNECT_ADDRESS), h.wallet.state.value)
    }

    @Test
    fun cancelBeforeTheTimeoutEndsTheFlowAndALaterAnswerIsIgnored() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        passTime(5_000)

        h.coordinator.cancel()
        runCurrent()

        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertNull(h.recovery.record)
        assertNull(h.overlays.current.value)

        h.wallet.respond(1, WalletOutcome.TransactionHash(hash1))
        passTime(60_000)
        assertEquals("a late answer for the abandoned request is ignored", TxState.Idle, h.coordinator.state.value)
        assertEquals(0, h.rpc.receiptRequests.size)
    }

    @Test
    fun cancelAfterTheTimeoutFailsTheFlowWithTheSessionReason() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        passTime(15_000)

        h.coordinator.cancel()
        runCurrent()

        assertEquals(TxState.Failed(FailReason.Timeout), h.coordinator.state.value)
        assertNull(h.recovery.record)
        // The sheet stays open to show the failure and its Reconnect wallet action.
        assertEquals(Overlays.TX_STATUS, h.overlays.current.value)
    }

    @Test
    fun confirmingTurnsSlowFifteenSecondsAfterTheHashAndNeverFails() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        h.rpc.script(hash1, FakeReceiptScript(nullPolls = 1_000_000))
        h.begin()
        runCurrent()
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)

        passTime(14_999)
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)
        passTime(1)
        assertEquals(TxState.ConfirmingSlow(hash1), h.coordinator.state.value)

        passTime(10 * 60_000L)
        assertEquals("ten minutes of nothing is still not a failure", TxState.ConfirmingSlow(hash1), h.coordinator.state.value)
        assertTrue("polling went on the whole time", h.rpc.receiptRequests.size > 290)
    }

    @Test
    fun aReceiptThatArrivesWhileSlowStillConfirms() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        h.rpc.script(hash1, FakeReceiptScript(nullPolls = 8))
        h.begin()
        runCurrent()

        passTime(15_000)
        assertEquals(TxState.ConfirmingSlow(hash1), h.coordinator.state.value)
        passTime(6_000)

        assertEquals(TxState.Confirmed(hash1, null), h.coordinator.state.value)
    }

    @Test
    fun keepWaitingForConfirmationArmsTheSlowMarkerAgain() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        h.rpc.script(hash1, FakeReceiptScript(nullPolls = 1_000_000))
        h.begin()
        runCurrent()
        passTime(15_000)
        assertEquals(TxState.ConfirmingSlow(hash1), h.coordinator.state.value)

        h.coordinator.keepWaitingConfirmation()
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)
        passTime(14_999)
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)
        passTime(1)
        assertEquals(TxState.ConfirmingSlow(hash1), h.coordinator.state.value)
    }

    @Test
    fun aNullReceiptKeepsPollingAndANetworkErrorWhilePollingChangesNothing() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        h.rpc.script(hash1, FakeReceiptScript(failedPolls = 3, nullPolls = 2))
        h.begin()
        runCurrent()

        val seen = mutableSetOf<TxState>()
        repeat(7) {
            passTime(2_000)
            seen += h.coordinator.state.value
        }

        assertTrue("no failure ever showed: $seen", seen.none { it is TxState.Failed })
        assertEquals(setOf<TxState>(TxState.Confirming(hash1)), seen - setOf<TxState>(TxState.Confirmed(hash1, null)))
        assertEquals(TxState.Confirmed(hash1, null), h.coordinator.state.value)
        assertEquals(6, h.rpc.receiptRequests.size)
    }

    @Test
    fun theSheetCannotBeCancelledWhileConfirming() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        h.rpc.script(hash1, FakeReceiptScript(nullPolls = 1_000_000))
        h.begin()
        runCurrent()

        h.coordinator.cancel()
        runCurrent()

        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)
    }
}
