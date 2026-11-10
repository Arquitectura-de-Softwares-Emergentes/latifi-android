package com.latifi.wallet.domain.tx

import com.latifi.wallet.core.Hex
import com.latifi.wallet.data.chain.ReceiptLogParser
import com.latifi.wallet.domain.chain.Receipt
import com.latifi.wallet.domain.chain.ReceiptPoller
import com.latifi.wallet.domain.session.SignOutHook
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletResponse
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.domain.wallet.WalletWaitTimer
import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * The state machine of one transaction, from the tap that confirms it to the receipt.
 *
 * ```
 * begin -> Pending -> (15 s) PendingTimeout -> keepWaiting -> Pending ...
 *             |                                  reconnect -> Pending (new request)
 *             | wallet returns a hash
 *             v
 *         Confirming -> (15 s) ConfirmingSlow ... never Failed because of delay
 *             | receipt 0x1 -> Confirmed        receipt 0x0 -> Failed(Reverted)
 * ```
 *
 * The wallet only ever answers with the id of the request it was given, so an answer for any other
 * id is ignored. The in-flight record is written as soon as the request id is known, gets the hash
 * when the wallet answers, and is cleared on Confirmed, on Failed and on Disconnect. The class
 * holds no key and signs nothing: the wallet signs, the app sends requests and reads receipts.
 *
 * Every state change goes through [lock] and carries a generation number, so a timer or an answer
 * that belongs to an abandoned run can never change the state of the current one.
 */
class TransactionCoordinator(
    private val scope: CoroutineScope,
    private val wallet: WalletGateway,
    private val preflight: Preflight,
    private val poller: ReceiptPoller,
    private val recovery: RecoveryStore,
    private val overlays: OverlayController,
    private val nowMillis: () -> Long,
    waitTimeoutMillis: Long = WalletWaitTimer.DEFAULT_TIMEOUT_MILLIS,
    private val slowAfterMillis: Long = SLOW_AFTER_MILLIS,
) : SignOutHook {
    /** One run of one transaction. Replaced, never edited, so a reader sees a consistent copy. */
    private data class Run(
        val gen: Int,
        val context: TxContext,
        val kind: TxKind,
        val step: TxStep?,
        val from: String?,
        val startedAt: Long,
        val requestId: Long? = null,
        val hash: String? = null,
    )

    private val lock = Any()
    private val recoveryMutex = Mutex()
    private val timer = WalletWaitTimer(scope, waitTimeoutMillis)

    private val mutableState = MutableStateFlow<TxState>(TxState.Idle)
    private val mutableContext = MutableStateFlow<TxContext?>(null)
    private val mutableSignals = MutableSharedFlow<TxSignal>(extraBufferCapacity = 8)

    private var generation = 0
    private var run: Run? = null
    private val earlyResponses = mutableListOf<WalletResponse>()

    private var sendJob: Job? = null
    private var pollJob: Job? = null
    private var slowJob: Job? = null
    private var reconnectJob: Job? = null

    val state: StateFlow<TxState> = mutableState.asStateFlow()

    /** What the flow told the sheet: the action name, the step line and the buttons. Null while idle. */
    val context: StateFlow<TxContext?> = mutableContext.asStateFlow()

    /** Things that are not failures, such as "run the approve step first". */
    val signals: SharedFlow<TxSignal> = mutableSignals.asSharedFlow()

    init {
        // Both collectors start now, so no answer or timeout emitted later is missed. The wallet
        // responses have no replay.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { wallet.responses.collect { onResponse(it) } }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            timer.state.collect { if (it == WalletWaitTimer.State.TimedOut) onWalletTimeout() }
        }
    }

    // ---- starting and the person's actions ----

    /**
     * Starts [step] and shows Pending at once. The preflight runs while Pending shows. Returns
     * false, and does nothing, when another transaction is still in flight.
     */
    fun begin(context: TxContext, step: TxStep, from: String): Boolean {
        val gen = locked {
            if (mutableState.value.isBusy) return false
            cancelJobs()
            generation += 1
            earlyResponses.clear()
            val now = nowMillis()
            run = Run(generation, context, step.kind, step, from, now)
            mutableContext.value = context
            mutableState.value = TxState.Pending(now)
            generation
        }
        overlays.show(Overlays.TX_STATUS)
        locked { sendJob = scope.launch { send(gen) } }
        return true
    }

    /** "Keep waiting for wallet": starts the 15 seconds again. It can be used as often as the person wants. */
    fun keepWaiting() {
        locked {
            if (mutableState.value !is TxState.PendingTimeout) return
            val started = run?.startedAt ?: nowMillis()
            mutableState.value = TxState.Pending(started)
        }
        timer.keepWaiting()
    }

    /**
     * "Reconnect wallet": opens the picker and, once the wallet is connected again, sends the same
     * step with a new request id and a new Pending time. The current state stays until then.
     */
    fun reconnect() {
        val gen = locked {
            val current = run ?: return
            val st = mutableState.value
            val allowed = st is TxState.PendingTimeout ||
                (st is TxState.Failed && (st.reason == FailReason.Session || st.reason == FailReason.Timeout))
            if (!allowed || current.step == null) return
            cancelJobs()
            bump()
        }
        timer.stop()
        locked {
            reconnectJob = scope.launch {
                wallet.openPicker()
                val connected = wallet.state.first { it is WalletState.Connected } as WalletState.Connected
                val ready = locked {
                    val current = run?.takeIf { it.gen == gen } ?: return@locked false
                    val now = nowMillis()
                    earlyResponses.clear()
                    run = current.copy(from = connected.address, requestId = null, hash = null, startedAt = now)
                    mutableState.value = TxState.Pending(now)
                    true
                }
                if (ready) {
                    overlays.show(Overlays.TX_STATUS)
                    send(gen)
                }
            }
        }
    }

    /** "Retry {action}": sends the same step again with a new request. */
    fun retry() {
        val again = locked {
            val current = run
            val step = current?.step
            if (mutableState.value !is TxState.Failed || current == null || step == null) {
                null
            } else {
                Triple(current.context, step, current.from)
            }
        }
        if (again == null) {
            dismiss()
            return
        }
        val from = (wallet.state.value as? WalletState.Connected)?.address ?: again.third ?: return dismiss()
        begin(again.first, again.second, from)
    }

    /**
     * "Cancel {action}". Before the timeout it ends the flow and a later answer for that request
     * is ignored. After the timeout it fails the flow with the session copy. On a finished
     * transaction it only closes the sheet. A transaction that is already confirming cannot be cancelled.
     */
    fun cancel() {
        when (mutableState.value) {
            is TxState.Pending -> abandon()
            is TxState.PendingTimeout -> {
                val gen = locked {
                    cancelJobs()
                    bump()
                }
                timer.stop()
                finish(gen, TxState.Failed(FailReason.Timeout))
            }
            is TxState.Failed, is TxState.Confirmed -> dismiss()
            else -> Unit
        }
    }

    /** "Keep waiting for confirmation": arms the slow marker again. Polling never stopped. */
    fun keepWaitingConfirmation() {
        locked {
            val st = mutableState.value as? TxState.ConfirmingSlow ?: return
            val gen = run?.gen ?: return
            mutableState.value = TxState.Confirming(st.hash)
            slowJob?.cancel()
            slowJob = armSlowMarker(gen, st.hash)
        }
    }

    /** Closes a finished transaction and the sheet. A transaction in flight is left alone. */
    fun dismiss() {
        locked {
            if (mutableState.value.isBusy) return
            cancelJobs()
            generation += 1
            run = null
            earlyResponses.clear()
            mutableContext.value = null
            mutableState.value = TxState.Idle
        }
        overlays.dismiss(Overlays.TX_STATUS)
    }

    /** "Back to home" while confirming: the sheet goes away and the tracking goes on. */
    fun hide() = overlays.dismiss(Overlays.TX_STATUS)

    /** Disconnect: forget the run, close the sheet and drop the record. */
    override suspend fun onSignOut() {
        locked {
            cancelJobs()
            generation += 1
            run = null
            earlyResponses.clear()
            mutableContext.value = null
            mutableState.value = TxState.Idle
        }
        timer.stop()
        recoveryMutex.withLock { recovery.clear() }
        overlays.dismiss(Overlays.TX_STATUS)
    }

    // ---- resume after a restart (driven by TransactionResumer) ----

    /** A stored record with a hash: the sheet reopens in Confirming and polling goes on. */
    fun resumeConfirming(record: RecoveryRecord): Boolean {
        val hash = record.txHash ?: return false
        val gen = locked {
            if (mutableState.value.isBusy) return false
            install(record, TxState.Confirming(hash))
        }
        overlays.show(Overlays.TX_STATUS)
        watch(gen, hash, record.kind)
        return true
    }

    /** A stored record with no hash yet: the wallet may still answer, so Pending shows and the 15 seconds restart. */
    fun resumePending(record: RecoveryRecord): Boolean {
        locked {
            if (mutableState.value.isBusy) return false
            install(record, TxState.Pending(record.startedAtMillis))
        }
        overlays.show(Overlays.TX_STATUS)
        timer.start()
        return true
    }

    /** True while the wallet answer for [requestId] is still awaited. */
    fun isWaitingFor(requestId: Long): Boolean = locked {
        val st = mutableState.value
        (st is TxState.Pending || st is TxState.PendingTimeout) && run?.requestId == requestId
    }

    /** Shows "Do not send it again" until the chain and the API say what happened to [record]. */
    fun markReconciling(record: RecoveryRecord): Boolean {
        locked {
            val st = mutableState.value
            if (st is TxState.Confirming || st is TxState.ConfirmingSlow || st is TxState.Confirmed) return false
            if (run?.requestId == record.requestId) {
                mutableState.value = TxState.Reconciling
            } else {
                if (st.isBusy) return false
                install(record, TxState.Reconciling)
            }
        }
        timer.stop()
        overlays.show(Overlays.TX_STATUS)
        return true
    }

    fun completeReconciled() {
        if (mutableState.value !is TxState.Reconciling) return
        val gen = locked { run?.gen } ?: return
        finish(gen, TxState.Confirmed(hash = null, loanId = null))
    }

    fun failReconciled() {
        if (mutableState.value !is TxState.Reconciling) return
        val gen = locked { run?.gen } ?: return
        finish(gen, TxState.Failed(FailReason.Timeout))
    }

    // ---- internals ----

    private suspend fun send(gen: Int) {
        val current = liveRun(gen) ?: return
        val step = current.step ?: return
        val from = current.from ?: return
        when (val check = preflight.check(step, from)) {
            PreflightResult.Ok -> Unit
            PreflightResult.NeedsAllowance -> {
                if (liveRun(gen) == null) return
                mutableSignals.emit(TxSignal.AllowanceNeeded(step))
                abandon(gen)
                return
            }
            is PreflightResult.Fail -> {
                finish(gen, TxState.Failed(check.reason, check.refreshInstallment))
                return
            }
        }
        if (liveRun(gen) == null) return
        timer.start()
        val handle = wallet.sendRequest(WalletRequest.SendTransaction(from, step.to, step.data, step.value))
        val id = handle.getOrNull()?.requestId
        if (id == null) {
            val reason = if (wallet.state.value is WalletState.Connected) FailReason.Declined else FailReason.Session
            finish(gen, TxState.Failed(reason))
            return
        }
        val early = locked {
            val running = run?.takeIf { it.gen == gen } ?: return
            run = running.copy(requestId = id)
            earlyResponses.firstOrNull { it.requestId == id }.also { earlyResponses.clear() }
        }
        persist(gen, id)
        if (early != null) onResponse(early)
    }

    /** Writes the record. It holds the hash too when the wallet answered before this ran. */
    private suspend fun persist(gen: Int, requestId: Long) {
        recoveryMutex.withLock {
            val current = liveRun(gen) ?: return
            val step = current.step ?: return
            recovery.save(
                RecoveryRecord(
                    requestId = requestId,
                    kind = current.kind,
                    action = current.context.action,
                    loanId = step.loanId,
                    amount = step.amount,
                    baseline = step.baseline,
                    calldataHash = sha256(step.data),
                    startedAtMillis = current.startedAt,
                    txHash = current.hash,
                ),
            )
        }
    }

    private fun onResponse(response: WalletResponse) {
        val current = locked {
            val st = mutableState.value
            if (!(st is TxState.Pending || st is TxState.PendingTimeout || st is TxState.Reconciling)) return
            val running = run ?: return
            val id = running.requestId
            if (id == null) {
                // The wallet answered before sendRequest returned. It is matched once the id is known.
                earlyResponses += response
                return
            }
            if (id != response.requestId) return
            running
        }
        when (val outcome = response.outcome) {
            is WalletOutcome.TransactionHash -> onHash(current, outcome.hex)
            WalletOutcome.Rejected -> finish(current.gen, TxState.Failed(FailReason.Declined))
            is WalletOutcome.Failed -> {
                val reason = if (outcome.message.contains("insufficient funds", ignoreCase = true)) {
                    FailReason.Fee
                } else {
                    FailReason.Declined
                }
                finish(current.gen, TxState.Failed(reason))
            }
            WalletOutcome.SessionLost -> finish(current.gen, TxState.Failed(FailReason.Session))
            // A signature is not an answer to a transaction request, so nothing was sent.
            is WalletOutcome.Signature -> finish(current.gen, TxState.Failed(FailReason.Declined))
        }
    }

    private fun onHash(current: Run, hex: String) {
        if (!Hex.isTxHash(hex)) {
            finish(current.gen, TxState.Failed(FailReason.Declined))
            return
        }
        val hash = hex.lowercase()
        val requestId = current.requestId ?: return
        val applied = locked {
            val running = run?.takeIf { it.gen == current.gen } ?: return
            val st = mutableState.value
            if (!(st is TxState.Pending || st is TxState.PendingTimeout || st is TxState.Reconciling)) return
            run = running.copy(hash = hash)
            mutableState.value = TxState.Confirming(hash)
            true
        }
        if (!applied) return
        timer.stop()
        scope.launch { recoveryMutex.withLock { recovery.update(requestId, hash) } }
        watch(current.gen, hash, current.kind)
    }

    private fun watch(gen: Int, hash: String, kind: TxKind) {
        locked {
            pollJob?.cancel()
            slowJob?.cancel()
            pollJob = scope.launch { onReceipt(gen, hash, kind, poller.poll(hash).first()) }
            slowJob = armSlowMarker(gen, hash)
        }
    }

    private fun armSlowMarker(gen: Int, hash: String): Job = scope.launch {
        delay(slowAfterMillis)
        locked {
            if (run?.gen == gen && mutableState.value == TxState.Confirming(hash)) {
                mutableState.value = TxState.ConfirmingSlow(hash)
            }
        }
    }

    private fun onReceipt(gen: Int, hash: String, kind: TxKind, receipt: Receipt) {
        if (receipt.succeeded) {
            val loanId = if (kind == TxKind.RequestLoan) {
                ReceiptLogParser.loanIdFromRequestReceipt(receipt.logs)
            } else {
                null
            }
            finish(gen, TxState.Confirmed(hash, loanId))
        } else {
            finish(gen, TxState.Failed(FailReason.Reverted))
        }
    }

    private fun onWalletTimeout() {
        locked {
            if (mutableState.value is TxState.Pending) mutableState.value = TxState.PendingTimeout
        }
    }

    /** Moves a run in flight to a final state. A run that was abandoned or replaced is left alone. */
    private fun finish(gen: Int, next: TxState): Boolean {
        val applied = locked {
            if (run?.gen != gen || !mutableState.value.isBusy) return@locked false
            slowJob?.cancel()
            pollJob?.cancel()
            mutableState.value = next
            true
        }
        if (!applied) return false
        timer.stop()
        scope.launch { recoveryMutex.withLock { recovery.clear() } }
        overlays.show(Overlays.TX_STATUS)
        return true
    }

    /** Ends the flow with no result: back to Idle, the record cleared and the sheet closed. */
    private fun abandon(gen: Int? = null) {
        val applied = locked {
            if (gen != null && run?.gen != gen) return@locked false
            cancelJobs()
            generation += 1
            run = null
            earlyResponses.clear()
            mutableContext.value = null
            mutableState.value = TxState.Idle
            true
        }
        if (!applied) return
        timer.stop()
        scope.launch { recoveryMutex.withLock { recovery.clear() } }
        overlays.dismiss(Overlays.TX_STATUS)
    }

    /** Sets the run that [record] describes, with no step to resend, and returns its generation. */
    private fun install(record: RecoveryRecord, st: TxState): Int {
        cancelJobs()
        generation += 1
        earlyResponses.clear()
        val context = TxContext(record.action)
        run = Run(
            gen = generation,
            context = context,
            kind = record.kind,
            step = null,
            from = null,
            startedAt = record.startedAtMillis,
            requestId = record.requestId,
            hash = record.txHash,
        )
        mutableContext.value = context
        mutableState.value = st
        return generation
    }

    /** Gives the current run a new generation so every pending callback of the old one is ignored. */
    private fun bump(): Int {
        generation += 1
        run = run?.copy(gen = generation)
        return generation
    }

    private fun cancelJobs() {
        sendJob?.cancel()
        pollJob?.cancel()
        slowJob?.cancel()
        reconnectJob?.cancel()
        sendJob = null
        pollJob = null
        slowJob = null
        reconnectJob = null
    }

    /** The run of [gen] while it is in flight, or null when it ended or was replaced. */
    private fun liveRun(gen: Int): Run? = locked {
        run?.takeIf { it.gen == gen && mutableState.value.isBusy }
    }

    private inline fun <T> locked(block: () -> T): T = synchronized(lock, block)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        /** After the hash, the receipt is late when it has not shown in 15 seconds. */
        const val SLOW_AFTER_MILLIS = 15_000L
    }
}
