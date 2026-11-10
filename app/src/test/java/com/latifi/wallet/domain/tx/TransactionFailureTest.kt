package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.chain.RevertDecoder
import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.domain.chain.CallOutcome
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.support.TxHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFailureTest {
    private fun TestScope.harness(script: FakeWalletScript = FakeWalletScript.NeverAnswer): TxHarness =
        TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }

    private fun word(value: Long) = value.toString(16).padStart(64, '0')

    private val addressWord = "0".repeat(24) + TxHarness.ADDRESS.removePrefix("0x")

    private fun revert(selector: String, vararg words: String) = selector + words.joinToString("")

    private suspend fun TestScope.failureOf(h: TxHarness): TxState {
        h.begin()
        advanceTimeBy(1_000)
        runCurrent()
        return h.coordinator.state.value
    }

    // ---- what the wallet says ----

    @Test
    fun rejectedInTheWalletIsDeclinedAndNothingWasSent() = runTest {
        val h = harness(FakeWalletScript.Reject(delayMillis = 100))
        assertEquals(TxState.Failed(FailReason.Declined), failureOf(h))
        assertEquals(0, h.rpc.receiptRequests.size)
        assertNull(h.recovery.record)
    }

    @Test
    fun anUnknownWalletErrorIsDeclined() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        h.wallet.respond(1, WalletOutcome.Failed(code = 4001, message = "User denied transaction signature"))
        runCurrent()
        assertEquals(TxState.Failed(FailReason.Declined), h.coordinator.state.value)
    }

    @Test
    fun insufficientFundsInTheWalletTextIsAFeeFailureWhateverTheCase() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        h.wallet.respond(1, WalletOutcome.Failed(code = -32000, message = "Insufficient Funds for gas * price + value"))
        runCurrent()
        assertEquals(TxState.Failed(FailReason.Fee), h.coordinator.state.value)
    }

    @Test
    fun aLostWalletSessionIsASessionFailure() = runTest {
        val h = harness(FakeWalletScript.SessionLost(delayMillis = 100))
        assertEquals(TxState.Failed(FailReason.Session), failureOf(h))
    }

    @Test
    fun aReceiptWithStatusZeroIsRevertedAndTheRecordIsCleared() = runTest {
        val h = harness(FakeWalletScript.AutoApprove(delayMillis = 0))
        h.rpc.script(TxHarness.hashOf(1), FakeReceiptScript(status = "0x0"))
        h.begin()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(TxState.Failed(FailReason.Reverted), h.coordinator.state.value)
        assertNull(h.recovery.record)
    }

    // ---- what the preflight says, before the wallet opens ----

    private suspend fun TestScope.preflightFailure(data: String?): Pair<TxState, TxHarness> {
        val h = harness()
        h.rpc.revertWith(data)
        h.begin()
        runCurrent()
        return h.coordinator.state.value to h
    }

    @Test
    fun theKnownRevertSelectorsMapToTheirReasonsBeforeAnyWalletRequest() = runTest {
        val cases = listOf(
            revert(RevertDecoder.LOAN_NOT_OPEN, word(1)) to FailReason.NotOpen,
            revert(RevertDecoder.INSUFFICIENT_BALANCE, addressWord, word(5), word(10)) to FailReason.Tokens,
            revert(RevertDecoder.SELF_FUNDING) to FailReason.Reverted,
            revert(RevertDecoder.LOAN_NOT_ACTIVE, word(1)) to FailReason.Reverted,
            revert(RevertDecoder.NOT_BORROWER, addressWord, addressWord) to FailReason.Reverted,
            revert(RevertDecoder.LOAN_NOT_FOUND, word(9)) to FailReason.Reverted,
            "0xdeadbeef" to FailReason.Reverted,
            null to FailReason.Reverted,
        )
        for ((data, reason) in cases) {
            val (state, h) = preflightFailure(data)
            assertEquals("revert data $data", TxState.Failed(reason), state)
            assertEquals("the wallet is never asked for $data", 0, h.wallet.sentRequests.size)
            assertNull(h.recovery.record)
        }
    }

    @Test
    fun anOverpaymentRevertFailsAndAsksTheFlowToRefreshTheInstallment() = runTest {
        val (state, h) = preflightFailure(revert(RevertDecoder.OVERPAYMENT, word(100), word(50)))
        assertEquals(TxState.Failed(FailReason.Reverted, refreshInstallment = true), state)
        assertEquals(0, h.wallet.sentRequests.size)
    }

    @Test
    fun anAllowanceShortfallIsASignalAndNotAFailure() = runTest {
        val h = harness()
        h.rpc.revertWith(revert(RevertDecoder.INSUFFICIENT_ALLOWANCE, addressWord, word(0), word(100)))
        val signals = mutableListOf<TxSignal>()
        backgroundScope.launch { h.coordinator.signals.collect { signals += it } }
        runCurrent()

        h.begin()
        runCurrent()

        assertEquals(listOf<TxSignal>(TxSignal.AllowanceNeeded(TxHarness.repayStep())), signals)
        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertEquals(0, h.wallet.sentRequests.size)
        assertNull(h.recovery.record)
    }

    @Test
    fun aWalletWithNoNativeCoinFailsWithTheFeeReasonBeforeTheWalletOpens() = runTest {
        val h = harness()
        h.rpc.setBalance(TxHarness.ADDRESS, BigInteger.ZERO)
        h.begin()
        runCurrent()
        assertEquals(TxState.Failed(FailReason.Fee), h.coordinator.state.value)
        assertEquals(0, h.wallet.sentRequests.size)
    }

    @Test
    fun aNetworkErrorInThePreflightIsANetworkFailure() = runTest {
        val h = harness()
        h.rpc.onCall = { _, _, _ -> Result.failure(AppException(AppError.Offline)) }
        h.begin()
        runCurrent()
        assertEquals(TxState.Failed(FailReason.Network), h.coordinator.state.value)
        assertEquals(0, h.wallet.sentRequests.size)
    }

    @Test
    fun aBalanceReadThatFailsInThePreflightIsANetworkFailure() = runTest {
        val h = harness()
        h.rpc.onCall = { _, _, _ -> Result.success(CallOutcome.Success("0x")) }
        val failing = object : com.latifi.wallet.domain.chain.RpcClient by h.rpc {
            override suspend fun getBalance(address: String) = Result.failure<BigInteger>(AppException(AppError.Offline))
        }
        val result = Preflight(failing).check(TxHarness.repayStep(), TxHarness.ADDRESS)
        assertEquals(PreflightResult.Fail(FailReason.Network), result)
    }

    @Test
    fun pendingShowsWhilePreflightRuns() = runTest {
        val h = harness()
        h.begin()
        assertTrue(h.coordinator.state.value is TxState.Pending)
    }

    // ---- what the person does next ----

    @Test
    fun retryAfterAFailureSendsTheSameStepAgainWithANewRequestId() = runTest {
        val h = harness(FakeWalletScript.Reject(delayMillis = 100))
        failureOf(h)
        assertEquals(TxState.Failed(FailReason.Declined), h.coordinator.state.value)

        h.wallet.script = FakeWalletScript.AutoApprove(delayMillis = 100)
        h.coordinator.retry()
        assertTrue("Pending shows at once on retry", h.coordinator.state.value is TxState.Pending)
        advanceTimeBy(150)
        runCurrent()

        assertEquals(listOf(1L, 2L), h.wallet.sentRequests.map { it.first })
        assertEquals(TxState.Confirming(TxHarness.hashOf(2)), h.coordinator.state.value)
    }

    @Test
    fun reconnectFromASessionFailureReconnectsThenSendsTheStepAgain() = runTest {
        val h = harness(FakeWalletScript.SessionLost(delayMillis = 100))
        failureOf(h)
        assertEquals(TxState.Failed(FailReason.Session), h.coordinator.state.value)

        h.wallet.script = FakeWalletScript.AutoApprove(delayMillis = 100)
        h.coordinator.reconnect()
        runCurrent()
        assertEquals("the failure stays until the wallet is back", TxState.Failed(FailReason.Session), h.coordinator.state.value)
        advanceTimeBy(600)
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Pending)
        advanceTimeBy(150)
        runCurrent()

        assertEquals(TxState.Confirming(TxHarness.hashOf(2)), h.coordinator.state.value)
    }

    @Test
    fun cancelOnAFailureClosesTheSheet() = runTest {
        val h = harness(FakeWalletScript.Reject(delayMillis = 100))
        failureOf(h)

        h.coordinator.cancel()

        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertNull(h.overlays.current.value)
    }
}
