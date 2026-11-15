package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.support.TxHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WR-07 on virtual time: a transaction that was dropped or replaced never leaves the app polling
 * for ever, the chain and the API get a look after each window, and a delay is never a failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionOverdueTest {
    private class StubReconciler(var completed: Boolean = false) : Reconciler {
        var asked = 0

        override suspend fun hasCompleted(record: RecoveryRecord): Boolean {
            asked += 1
            return completed
        }
    }

    private val hash1 = TxHarness.hashOf(1)

    private fun TestScope.harness(reconciler: Reconciler? = null): TxHarness =
        TxHarness(backgroundScope, testScheduler, reconciler).also {
            it.wallet.script = FakeWalletScript.AutoApprove(delayMillis = 0)
            it.rpc.script(hash1, FakeReceiptScript(nullPolls = 1_000_000))
        }

    private suspend fun TestScope.passTime(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private val allWindows = TransactionCoordinator.RECEIPT_WINDOW_MILLIS * TransactionCoordinator.RECEIPT_WINDOWS

    @Test
    fun theReceiptPollingStopsAfterItsWindowsAndTheStateIsSlowNeverFailed() = runTest {
        val reconciler = StubReconciler(completed = false)
        val h = harness(reconciler)
        h.begin()
        runCurrent()

        passTime(allWindows + 120_000)

        assertEquals(TxState.ConfirmingSlow(hash1), h.coordinator.state.value)
        assertTrue("the chain and the API were asked after the windows", reconciler.asked >= TransactionCoordinator.RECEIPT_WINDOWS)
        val polls = h.rpc.receiptRequests.size

        passTime(60 * 60_000L)

        assertEquals("no poll after the windows", polls, h.rpc.receiptRequests.size)
        assertEquals(TxState.ConfirmingSlow(hash1), h.coordinator.state.value)
        assertTrue("the record stays, so the next start picks it up", h.recovery.record != null)
    }

    @Test
    fun theWindowsAreBoundedInNumberOfCalls() = runTest {
        val h = harness()
        h.begin()
        runCurrent()

        passTime(allWindows + 120_000)

        // 10 minutes every 2 seconds, then two windows of 10 minutes every 30 seconds.
        assertTrue("${h.rpc.receiptRequests.size} polls", h.rpc.receiptRequests.size in 300..400)
    }

    @Test
    fun aReplacedTransactionWithTheSameEffectEndsAsConfirmedAfterTheFirstWindow() = runTest {
        val reconciler = StubReconciler(completed = false)
        val h = harness(reconciler)
        h.begin()
        runCurrent()
        passTime(TransactionCoordinator.RECEIPT_WINDOW_MILLIS - 60_000)
        assertTrue(h.coordinator.state.value is TxState.ConfirmingSlow)

        // The person sped the transaction up in the wallet: another hash, the same effect.
        reconciler.completed = true
        passTime(120_000)

        assertEquals(TxState.Confirmed(hash1, null), h.coordinator.state.value)
        assertNull("the record is cleared", h.recovery.record)
    }

    @Test
    fun keepWaitingForConfirmationStartsPollingAgainAfterTheWindows() = runTest {
        val h = harness(StubReconciler(completed = false))
        h.begin()
        runCurrent()
        passTime(allWindows + 120_000)
        assertEquals(TxState.ConfirmingSlow(hash1), h.coordinator.state.value)
        val polls = h.rpc.receiptRequests.size

        h.coordinator.keepWaitingConfirmation()
        passTime(TransactionCoordinator.LATE_POLL_MILLIS * 2)

        assertTrue("polling restarted", h.rpc.receiptRequests.size > polls)
    }

    @Test
    fun aReceiptThatArrivesInALaterWindowStillConfirms() = runTest {
        val h = harness(StubReconciler(completed = false))
        // About 300 polls fill the first window, ten more fall in the second, then the receipt shows.
        h.rpc.script(hash1, FakeReceiptScript(nullPolls = 310))
        h.begin()
        runCurrent()

        passTime(allWindows)

        assertEquals(TxState.Confirmed(hash1, null), h.coordinator.state.value)
    }
}
