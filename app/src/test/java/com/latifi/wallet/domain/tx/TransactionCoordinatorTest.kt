package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.LogEntry
import com.latifi.wallet.data.chain.ReceiptLogParser
import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
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
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCoordinatorTest {
    private fun TestScope.harness(script: FakeWalletScript = FakeWalletScript.AutoApprove(delayMillis = 400)): TxHarness =
        TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }

    private val hash1 = TxHarness.hashOf(1)

    @Test
    fun pendingIsSetAtTheInstantOfBeginAndTheSheetOpens() = runTest {
        val h = harness()

        val started = h.begin()

        assertTrue(started)
        assertEquals("no time has passed and nothing has run yet", TxState.Pending(0), h.coordinator.state.value)
        assertEquals(Overlays.TX_STATUS, h.overlays.current.value)
        assertEquals(TxAction.Payment, h.coordinator.context.value?.action)
    }

    @Test
    fun theWalletIsAskedOnceWithTheStepAndTheSender() = runTest {
        val h = harness()

        h.begin()
        runCurrent()

        val sent = h.wallet.sentRequests.single().second as WalletRequest.SendTransaction
        assertEquals(TxHarness.ADDRESS, sent.from)
        assertEquals(Deployment.LOANBOOK_ADDRESS, sent.to)
        assertEquals("0xabcdef01", sent.data)
        assertEquals("0x0", sent.value)
    }

    @Test
    fun aHashGivesConfirmingAndAReceiptWithStatusOneGivesConfirmed() = runTest {
        val h = harness()

        h.begin()
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Pending)

        advanceTimeBy(400)
        runCurrent()
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)

        // The default fake node answers null twice and then a successful receipt: polls at 2, 4 and 6 seconds after the hash.
        advanceTimeBy(5_999)
        runCurrent()
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(TxState.Confirmed(hash1, null), h.coordinator.state.value)
    }

    @Test
    fun theReceiptIsPolledEveryTwoSecondsAfterTheHash() = runTest {
        val h = harness()
        h.rpc.script(hash1, FakeReceiptScript(nullPolls = 100))

        h.begin()
        advanceTimeBy(400 + 10_000)
        runCurrent()

        assertEquals(5, h.rpc.receiptRequests.size)
    }

    @Test
    fun aRequestLoanStepYieldsTheLoanIdFromTheLog() = runTest {
        val h = harness()
        val log = LogEntry(
            address = Deployment.LOANBOOK_ADDRESS,
            topics = listOf(ReceiptLogParser.LOAN_REQUESTED_TOPIC, "0x" + "7".padStart(64, '0')),
            data = "0x",
        )
        h.rpc.script(hash1, FakeReceiptScript(logs = listOf(log)))

        h.begin(action = TxAction.Request, step = TxHarness.requestStep())
        advanceTimeBy(400 + 2_000)
        runCurrent()

        assertEquals(TxState.Confirmed(hash1, BigInteger.valueOf(7)), h.coordinator.state.value)
    }

    @Test
    fun aLogOnAStepThatIsNotARequestIsNotReadAsALoanId() = runTest {
        val h = harness()
        val log = LogEntry(
            address = Deployment.LOANBOOK_ADDRESS,
            topics = listOf(ReceiptLogParser.LOAN_REQUESTED_TOPIC, "0x" + "7".padStart(64, '0')),
            data = "0x",
        )
        h.rpc.script(hash1, FakeReceiptScript(logs = listOf(log)))

        h.begin()
        advanceTimeBy(400 + 2_000)
        runCurrent()

        assertEquals(TxState.Confirmed(hash1, null), h.coordinator.state.value)
    }

    @Test
    fun aResponseForAnotherRequestIdIsIgnored() = runTest {
        val h = harness(FakeWalletScript.NeverAnswer)

        h.begin()
        runCurrent()
        h.wallet.respond(requestId = 99, outcome = WalletOutcome.TransactionHash(TxHarness.hashOf(99)))
        runCurrent()
        assertTrue("a foreign answer changes nothing", h.coordinator.state.value is TxState.Pending)

        h.wallet.respond(requestId = 1, outcome = WalletOutcome.TransactionHash(hash1))
        runCurrent()
        assertEquals(TxState.Confirming(hash1), h.coordinator.state.value)
    }

    @Test
    fun aMalformedReferenceFromTheWalletNeverStartsPolling() = runTest {
        val h = harness(FakeWalletScript.NeverAnswer)

        h.begin()
        runCurrent()
        h.wallet.respond(1, WalletOutcome.TransactionHash("0x1234"))
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(TxState.Failed(FailReason.Declined), h.coordinator.state.value)
        assertEquals(0, h.rpc.receiptRequests.size)
    }

    @Test
    fun aSecondBeginWhileOneIsInFlightIsRefused() = runTest {
        val h = harness(FakeWalletScript.NeverAnswer)

        assertTrue(h.begin())
        runCurrent()
        assertFalse(h.begin())
        runCurrent()

        assertEquals(1, h.wallet.sentRequests.size)
    }

    @Test
    fun theRecordIsWrittenWithTheRequestIdFirstAndTheHashLaterThenCleared() = runTest {
        val h = harness()

        h.begin()
        runCurrent()
        val first = h.recovery.record!!
        assertEquals(1L, first.requestId)
        assertNull("the hash does not exist yet", first.txHash)
        assertEquals(TxKind.Repay, first.kind)
        assertEquals(BigInteger.ONE, first.loanId)
        assertEquals(BigInteger.valueOf(100_000_000), first.amount)
        assertEquals(BigInteger.ZERO, first.baseline)
        assertEquals(TxAction.Payment, first.action)
        assertEquals(64, first.calldataHash.length)

        advanceTimeBy(400)
        runCurrent()
        assertEquals(hash1, h.recovery.record?.txHash)
        assertEquals(1, h.recovery.saves.size)

        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Confirmed)
        assertNull("the record is cleared on Confirmed", h.recovery.record)
    }

    @Test
    fun dismissAfterConfirmedReturnsToIdleAndClosesTheSheet() = runTest {
        val h = harness()
        h.begin()
        advanceTimeBy(400 + 6_000)
        runCurrent()

        h.coordinator.dismiss()

        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertNull(h.coordinator.context.value)
        assertNull(h.overlays.current.value)
    }

    @Test
    fun theSecondStepOfAFlowStartsFromConfirmed() = runTest {
        val h = harness()
        h.begin(context = TxContext(TxAction.Payment, stepIndex = 1, stepCount = 2))
        advanceTimeBy(400 + 6_000)
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Confirmed)

        val started = h.begin(context = TxContext(TxAction.Payment, stepIndex = 2, stepCount = 2))

        assertTrue(started)
        assertTrue(h.coordinator.state.value is TxState.Pending)
        assertEquals(2, h.coordinator.context.value?.stepIndex)
    }

    @Test
    fun aFinishedStepClearsOnlyItsOwnRecordNeverTheNextStepsRecord() = runTest {
        val h = harness()
        h.recovery.clearDelayMillis = 5_000
        h.begin(context = TxContext(TxAction.Payment, stepIndex = 1, stepCount = 2))
        advanceTimeBy(400 + 6_000)
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Confirmed)

        // The slow clear of step 1 is still running when step 2 starts and writes its record.
        assertTrue(h.begin(context = TxContext(TxAction.Payment, stepIndex = 2, stepCount = 2)))
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(listOf(1L), h.recovery.conditionalClears.take(1))
        assertEquals("the record of the second request survives", 2L, h.recovery.record?.requestId)
        assertEquals("no unconditional clear ran", 0, h.recovery.clears)
    }

    @Test
    fun abandoningARunClearsItsOwnRecordWithTheConditionalClear() = runTest {
        val h = harness(FakeWalletScript.NeverAnswer)
        h.begin()
        runCurrent()
        assertEquals(1L, h.recovery.record?.requestId)

        h.coordinator.cancel()
        runCurrent()

        assertNull(h.recovery.record)
        assertEquals(listOf(1L), h.recovery.conditionalClears)
        assertEquals(0, h.recovery.clears)
    }

    @Test
    fun disconnectForgetsTheRunClosesTheSheetAndClearsTheRecord() = runTest {
        val h = harness(FakeWalletScript.NeverAnswer)
        h.begin()
        runCurrent()
        assertTrue(h.recovery.record != null)

        h.coordinator.onSignOut()

        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertNull(h.recovery.record)
        assertNull(h.overlays.current.value)
    }
}
