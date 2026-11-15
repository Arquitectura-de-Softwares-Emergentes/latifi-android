package com.latifi.wallet.domain.tx

import androidx.annotation.StringRes
import java.math.BigInteger

/** What one transaction does on chain. */
enum class TxKind { Mint, RequestLoan, Approve, Fund, Repay }

/**
 * The flow a transaction belongs to. It names the action in the copy of the status sheet
 * (`tx_action_*`), so "Cancel payment" and "Retry funding" say what they act on.
 */
enum class TxAction { Coins, Request, Payment, Funding }

/**
 * One transaction the wallet is asked to send. [data] is the calldata, [value] is `0x0` because
 * nothing in the app moves the native coin. [amount] and [baseline] are only used to reconcile
 * after a restart: the amount the step moves and the balance, allowance or repaid amount seen
 * before it ran.
 */
data class TxStep(
    val kind: TxKind,
    val to: String,
    val data: String,
    val loanId: BigInteger? = null,
    val value: String = "0x0",
    val amount: BigInteger? = null,
    val baseline: BigInteger? = null,
)

/** The button a flow shows on the Confirmed state, for example "View my loans". */
class TxPrimary(@StringRes val labelRes: Int, val onClick: () -> Unit)

/**
 * What the status sheet needs to know about the flow that started a transaction: the action name,
 * the position in a two step flow, the button of the Confirmed state and where to go back to.
 * A flow that does not pass a callback gets the plain sheet behavior: the sheet closes.
 */
data class TxContext(
    val action: TxAction,
    val stepIndex: Int = 1,
    val stepCount: Int = 1,
    val confirmedPrimary: TxPrimary? = null,
    @StringRes val successTitleRes: Int? = null,
    @StringRes val successBodyRes: Int? = null,
    /** The exact amount of an approve step, as text without the unit. The sheet says what is being allowed. */
    val allowAmountText: String? = null,
    val onBackToHome: (() -> Unit)? = null,
    val onBackToRequests: (() -> Unit)? = null,
)

/** Why a transaction failed, in the vocabulary of the copy. Delay alone is never a reason. */
enum class FailReason { Declined, Session, Fee, Tokens, Reverted, NotOpen, Network, Timeout }

sealed interface TxState {
    data object Idle : TxState

    /** The person confirmed and the wallet has been asked. [startedAtMillis] is the wall clock of that moment. */
    data class Pending(val startedAtMillis: Long) : TxState

    /** 15 seconds went by with no answer from the wallet. Nothing was discarded. */
    data object PendingTimeout : TxState

    /** The wallet sent the transaction. The app polls for the receipt. */
    data class Confirming(val hash: String) : TxState

    /** 15 seconds after the hash and still no receipt. It keeps polling and never becomes a failure. */
    data class ConfirmingSlow(val hash: String) : TxState

    /** After a restart, the app compares the chain and the API with the stored record. */
    data object Reconciling : TxState

    /** [hash] is null when the result came from reconciliation, so there is no receipt to show. */
    data class Confirmed(val hash: String?, val loanId: BigInteger?) : TxState

    /** [refreshInstallment] asks the flow to reload the next installment before a retry. */
    data class Failed(val reason: FailReason, val refreshInstallment: Boolean = false) : TxState
}

/** A transaction is in flight: the sheet cannot be dismissed and a second one cannot start. */
val TxState.isBusy: Boolean
    get() = this is TxState.Pending || this is TxState.PendingTimeout || this is TxState.Confirming ||
        this is TxState.ConfirmingSlow || this is TxState.Reconciling

/** Things the coordinator tells the flow that are not failures. */
sealed interface TxSignal {
    /** The preflight found the allowance too low, so the flow must run the approve step first. */
    data class AllowanceNeeded(val step: TxStep) : TxSignal
}
