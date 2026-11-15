package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Decides, with the chain and the API, whether a transaction whose answer was lost actually went
 * through. It never throws: a read that fails is "not yet", and the caller asks again.
 */
interface Reconciler {
    suspend fun hasCompleted(record: RecoveryRecord): Boolean
}

/**
 * Reconciles by kind: a mint by the token balance above the baseline, an approval by the allowance
 * covering the amount, a loan request by a loan of this borrower requested since the start, a
 * funding by the loan being active with this lender, and a payment by the repaid amount above the
 * baseline. The request time is compared with a minute of slack, because the device clock and the
 * block time never agree exactly, and a missed match would invite a second send.
 */
class ChainReconciler(
    private val chain: ChainReadRepository,
    private val loans: LoanRepository,
    private val sessions: SessionStore,
) : Reconciler {
    override suspend fun hasCompleted(record: RecoveryRecord): Boolean {
        val owner = sessions.current()?.address ?: return false
        return when (record.kind) {
            TxKind.Mint -> {
                val baseline = record.baseline ?: return false
                chain.tokenBalance(owner).getOrNull()?.baseUnits?.let { it > baseline } ?: false
            }
            TxKind.Approve -> {
                val amount = record.amount ?: return false
                chain.allowance(owner, Deployment.LOANBOOK_ADDRESS).getOrNull()?.let { it >= amount } ?: false
            }
            TxKind.RequestLoan -> {
                val since = Instant.ofEpochMilli(record.startedAtMillis - SLACK_MILLIS)
                val query = LoanQuery(borrower = owner, sort = LoanSort.Recent, size = RECENT_LOANS)
                loans.list(query).getOrNull()?.items?.any { !it.requestedAt.isBefore(since) } ?: false
            }
            TxKind.Fund -> {
                val loanId = record.loanId ?: return false
                loans.get(loanId).getOrNull()?.let {
                    it.status == LoanStatus.ACTIVE && it.lender.equals(owner, ignoreCase = true)
                } ?: false
            }
            TxKind.Repay -> {
                val loanId = record.loanId ?: return false
                val baseline = record.baseline ?: return false
                loans.get(loanId).getOrNull()?.let { it.amountRepaid > baseline } ?: false
            }
        }
    }

    private companion object {
        const val SLACK_MILLIS = 60_000L
        const val RECENT_LOANS = 5
    }
}

/**
 * After a process kill, picks the stored in-flight record up again. The application calls
 * [resume] once the wallet delegate is set (plan 05-14).
 *
 * - A hash is stored: the sheet reopens in Confirming and polling goes on.
 * - No hash and under 5 minutes old: Pending shows and the wallet may still answer. If it has
 *   not by the fifth minute, the record is reconciled.
 * - No hash and older: it is reconciled at once. "Do not send it again" shows until the chain and
 *   the API answer, with the indexer lag wait. An unresolved record ends as a failure.
 */
class TransactionResumer(
    private val store: RecoveryStore,
    private val coordinator: TransactionCoordinator,
    private val reconciler: Reconciler,
    private val waiter: IndexerLagWaiter,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) {
    fun resume(): Job = scope.launch {
        val record = store.current() ?: return@launch
        val age = nowMillis() - record.startedAtMillis
        when {
            record.txHash != null -> coordinator.resumeConfirming(record)
            age < LATE_ANSWER_MILLIS -> {
                if (!coordinator.resumePending(record)) return@launch
                delay(LATE_ANSWER_MILLIS - age)
                if (coordinator.isWaitingFor(record.requestId)) reconcile(record)
            }
            else -> reconcile(record)
        }
    }

    private suspend fun reconcile(record: RecoveryRecord) {
        if (!coordinator.markReconciling(record)) return
        val done = waiter.await { reconciler.hasCompleted(record) }
        if (done) coordinator.completeReconciled() else coordinator.failReconciled()
    }

    companion object {
        /** How long a wallet answer can be late before the record is reconciled instead. */
        const val LATE_ANSWER_MILLIS = 5 * 60_000L
    }
}
