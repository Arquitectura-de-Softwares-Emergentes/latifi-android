package com.latifi.wallet.domain.tx

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.chain.NextInstallment
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.Page
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.support.TxHarness
import com.latifi.wallet.ui.nav.Overlays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionResumerTest {
    private val hash = TxHarness.hashOf(7)

    private fun record(kind: TxKind = TxKind.Repay, ageMillis: Long, txHash: String? = null) = RecoveryRecord(
        requestId = 7,
        kind = kind,
        action = TxAction.Payment,
        loanId = BigInteger.ONE,
        amount = BigInteger.valueOf(100),
        baseline = BigInteger.ZERO,
        calldataHash = "0".repeat(64),
        startedAtMillis = -ageMillis,
        txHash = txHash,
    )

    /** Answers by kind: the kinds in [done] completed, the rest never did. Counts the questions. */
    private class ScriptedReconciler(private val done: Set<TxKind>) : Reconciler {
        var asked = 0
        override suspend fun hasCompleted(record: RecoveryRecord): Boolean {
            asked += 1
            return record.kind in done
        }
    }

    private fun TestScope.resumer(h: TxHarness, reconciler: Reconciler) = TransactionResumer(
        store = h.recovery,
        coordinator = h.coordinator,
        reconciler = reconciler,
        waiter = IndexerLagWaiter(),
        scope = backgroundScope,
        nowMillis = { testScheduler.currentTime },
    )

    private fun TestScope.harness() = TxHarness(backgroundScope, testScheduler)

    @Test
    fun aStoredHashReopensTheSheetInConfirmingAndPollsToTheReceipt() = runTest {
        val h = harness()
        h.recovery.save(record(ageMillis = 60_000, txHash = hash))

        resumer(h, ScriptedReconciler(emptySet())).resume()
        runCurrent()

        assertEquals(TxState.Confirming(hash), h.coordinator.state.value)
        assertEquals(Overlays.TX_STATUS, h.overlays.current.value)
        assertEquals(TxAction.Payment, h.coordinator.context.value?.action)

        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(TxState.Confirmed(hash, null), h.coordinator.state.value)
        assertNull("the record is cleared when the receipt arrives", h.recovery.record)
    }

    @Test
    fun aStoredHashWithARevertedReceiptFails() = runTest {
        val h = harness()
        h.rpc.script(hash, FakeReceiptScript(status = "0x0"))
        h.recovery.save(record(ageMillis = 60_000, txHash = hash))

        resumer(h, ScriptedReconciler(emptySet())).resume()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(TxState.Failed(FailReason.Reverted), h.coordinator.state.value)
    }

    @Test
    fun noRecordMeansNothingHappens() = runTest {
        val h = harness()

        resumer(h, ScriptedReconciler(emptySet())).resume()
        runCurrent()

        assertEquals(TxState.Idle, h.coordinator.state.value)
        assertNull(h.overlays.current.value)
    }

    @Test
    fun noHashAndTwoMinutesOldWaitsInPendingAndAcceptsALateAnswer() = runTest {
        val h = harness()
        val reconciler = ScriptedReconciler(emptySet())
        h.recovery.save(record(ageMillis = 120_000))

        resumer(h, reconciler).resume()
        runCurrent()
        assertEquals(TxState.Pending(-120_000), h.coordinator.state.value)

        h.wallet.respond(7, WalletOutcome.TransactionHash(hash))
        runCurrent()

        assertEquals(TxState.Confirming(hash), h.coordinator.state.value)
        assertEquals("the answer came, so nothing is reconciled", 0, reconciler.asked)
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(0, reconciler.asked)
    }

    @Test
    fun theFifteenSecondTimeoutPanelAppearsWhileWaitingForALateAnswer() = runTest {
        val h = harness()
        h.recovery.save(record(ageMillis = 60_000))

        resumer(h, ScriptedReconciler(emptySet())).resume()
        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(TxState.PendingTimeout, h.coordinator.state.value)
    }

    @Test
    fun ifTheAnswerNeverComesTheRecordIsReconciledAtTheFifthMinute() = runTest {
        val h = harness()
        val reconciler = ScriptedReconciler(setOf(TxKind.Repay))
        h.recovery.save(record(ageMillis = 120_000))

        resumer(h, reconciler).resume()
        advanceTimeBy(3 * 60_000L - 1)
        runCurrent()
        assertTrue(h.coordinator.state.value is TxState.Pending || h.coordinator.state.value == TxState.PendingTimeout)
        assertEquals(0, reconciler.asked)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(TxState.Confirmed(null, null), h.coordinator.state.value)
        assertEquals(1, reconciler.asked)
    }

    @Test
    fun aSixMinuteOldRecordIsReconciledAtOnceForEveryKindAndShowsDoNotSendItAgainMeanwhile() = runTest {
        for (kind in TxKind.entries) {
            val h = harness()
            h.recovery.save(record(kind = kind, ageMillis = 6 * 60_000L))
            val reconciler = ScriptedReconciler(setOf(kind))

            resumer(h, reconciler).resume()
            runCurrent()

            assertEquals("$kind", TxState.Confirmed(null, null), h.coordinator.state.value)
            assertNull("$kind clears the record", h.recovery.record)
        }
    }

    @Test
    fun anUnresolvedRecordEndsAsAFailureAfterThirtySecondsOfLag() = runTest {
        val h = harness()
        val reconciler = ScriptedReconciler(emptySet())
        h.recovery.save(record(ageMillis = 6 * 60_000L))

        resumer(h, reconciler).resume()
        runCurrent()
        assertEquals("the copy says do not send it again", TxState.Reconciling, h.coordinator.state.value)

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(TxState.Reconciling, h.coordinator.state.value)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(TxState.Failed(FailReason.Timeout), h.coordinator.state.value)
        assertEquals("one check now and one every three seconds", 11, reconciler.asked)
        assertNull(h.recovery.record)
    }

    @Test
    fun aRecordIsNotResumedOverATransactionInFlight() = runTest {
        val h = harness()
        h.begin()
        runCurrent()
        h.recovery.save(record(ageMillis = 6 * 60_000L, txHash = hash))

        assertFalse(h.coordinator.resumeConfirming(record(ageMillis = 1, txHash = hash)))
    }

    // ---- ChainReconciler: what each kind compares ----

    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val owner = FakeData.DEMO_ADDRESS

    private class StubChain(var balance: BigInteger = BigInteger.ZERO, var allowance: BigInteger = BigInteger.ZERO) :
        ChainReadRepository {
        var failing = false

        override suspend fun tokenBalance(address: String) =
            if (failing) Result.failure(AppException(AppError.Offline)) else Result.success(TokenAmount(balance))

        override suspend fun polBalance(address: String) = Result.success(BigInteger.TEN)

        override suspend fun allowance(owner: String, spender: String) =
            if (failing) Result.failure(AppException(AppError.Offline)) else Result.success(allowance)

        override suspend fun nextInstallment(loanId: BigInteger): Result<NextInstallment> =
            Result.failure(AppException(AppError.NotFound))
    }

    private class StubLoans(var loans: List<Loan>) : LoanRepository {
        var failing = false

        override suspend fun list(query: LoanQuery): Result<Page<Loan>> =
            if (failing) Result.failure(AppException(AppError.Offline)) else Result.success(Page(loans, 0, 20, loans.size.toLong()))

        override suspend fun get(loanId: BigInteger): Result<Loan> =
            if (failing) {
                Result.failure(AppException(AppError.Offline))
            } else {
                loans.firstOrNull { it.loanId == loanId }?.let { Result.success(it) } ?: Result.failure(AppException(AppError.NotFound))
            }
    }

    private class StubSession(private val address: String?) : SessionStore {
        override val session: Flow<Session?> = flowOf(null)

        override suspend fun save(session: Session) = Unit

        override suspend fun clear() = Unit

        override suspend fun current(): Session? =
            address?.let { Session(it, "token", Instant.parse("2030-01-01T00:00:00Z")) }
    }

    private fun loan(status: LoanStatus, lender: String?, paid: Int = 0, daysAgo: Long = 1) = FakeData.loan(
        clock = clock, id = 1, borrower = owner, lender = lender, tokens = 100, bps = 1000, termDays = 30,
        installments = 2, status = status, paid = paid, requestedDaysAgo = daysAgo,
        fundedDaysAgo = if (status == LoanStatus.OPEN) null else 5,
    )

    private fun reconciler(chain: StubChain = StubChain(), loans: StubLoans = StubLoans(emptyList()), address: String? = owner) =
        ChainReconciler(chain, loans, StubSession(address))

    @Test
    fun aMintCompletedWhenTheBalanceIsAboveTheBaseline() = runTest {
        val chain = StubChain(balance = BigInteger.valueOf(500))
        val record = record(TxKind.Mint, 1).copy(baseline = BigInteger.valueOf(500))
        assertFalse(reconciler(chain).hasCompleted(record))
        chain.balance = BigInteger.valueOf(1_500)
        assertTrue(reconciler(chain).hasCompleted(record))
    }

    @Test
    fun aMintWithNoBaselineIsNeverReadAsCompleted() = runTest {
        val chain = StubChain(balance = BigInteger.valueOf(1_500))
        val record = record(TxKind.Mint, 1).copy(baseline = null)

        assertFalse("a balance with nothing to compare against proves nothing", reconciler(chain).hasCompleted(record))
    }

    @Test
    fun anApprovalCompletedWhenTheAllowanceCoversTheAmount() = runTest {
        val chain = StubChain(allowance = BigInteger.valueOf(99))
        val record = record(TxKind.Approve, 1).copy(amount = BigInteger.valueOf(100))
        assertFalse(reconciler(chain).hasCompleted(record))
        chain.allowance = BigInteger.valueOf(100)
        assertTrue(reconciler(chain).hasCompleted(record))
    }

    @Test
    fun aLoanRequestCompletedWhenALoanOfThisBorrowerWasRequestedSinceTheStart() = runTest {
        // The record started at "now"; a loan from yesterday is an older request, a loan from now is this one.
        val start = clock.millis()
        val record = record(TxKind.RequestLoan, 0).copy(startedAtMillis = start)
        val old = loan(LoanStatus.OPEN, lender = null, daysAgo = 1)
        val fresh = loan(LoanStatus.OPEN, lender = null, daysAgo = 0)
        assertFalse(reconciler(loans = StubLoans(listOf(old))).hasCompleted(record))
        assertTrue(reconciler(loans = StubLoans(listOf(fresh))).hasCompleted(record))
    }

    @Test
    fun aFundingCompletedWhenTheLoanIsActiveWithThisLender() = runTest {
        val record = record(TxKind.Fund, 1)
        assertFalse(reconciler(loans = StubLoans(listOf(loan(LoanStatus.OPEN, null)))).hasCompleted(record))
        assertFalse(reconciler(loans = StubLoans(listOf(loan(LoanStatus.ACTIVE, FakeData.LENDER_A)))).hasCompleted(record))
        assertTrue(reconciler(loans = StubLoans(listOf(loan(LoanStatus.ACTIVE, owner.uppercase().replace("0X", "0x")))))
            .hasCompleted(record))
    }

    @Test
    fun aPaymentCompletedWhenTheRepaidAmountIsAboveTheBaseline() = runTest {
        val record = record(TxKind.Repay, 1).copy(baseline = BigInteger.ZERO)
        assertFalse(reconciler(loans = StubLoans(listOf(loan(LoanStatus.ACTIVE, FakeData.LENDER_A, paid = 0)))).hasCompleted(record))
        assertTrue(reconciler(loans = StubLoans(listOf(loan(LoanStatus.ACTIVE, FakeData.LENDER_A, paid = 1)))).hasCompleted(record))
    }

    @Test
    fun aReadThatFailsIsNotYetAndNobodyIsSignedInMeansNo() = runTest {
        val chain = StubChain(allowance = BigInteger.valueOf(500)).apply { failing = true }
        val loans = StubLoans(listOf(loan(LoanStatus.ACTIVE, FakeData.LENDER_A, paid = 1))).apply { failing = true }
        assertFalse(reconciler(chain, loans).hasCompleted(record(TxKind.Approve, 1)))
        assertFalse(reconciler(chain, loans).hasCompleted(record(TxKind.Repay, 1)))
        assertFalse(reconciler(chain, loans).hasCompleted(record(TxKind.Mint, 1)))
        assertFalse(reconciler(StubChain(allowance = BigInteger.valueOf(500)), address = null).hasCompleted(record(TxKind.Approve, 1)))
    }
}
