package com.latifi.wallet.ui.pay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.R
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.rates.ExchangeRateRepository
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.tx.IndexerLagWaiter
import com.latifi.wallet.domain.tx.TransactionCoordinator
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxPlanRunner
import com.latifi.wallet.domain.tx.TxPlans
import com.latifi.wallet.domain.tx.TxPrimary
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.loans.LoanDetailViewModel
import com.latifi.wallet.ui.nav.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * What S10 needs: the loan, the installment the contract is asking for and the allowance now.
 * [unpaid] is the unpaid amount of the current installment from `nextInstallment`, never the total
 * and never more, because `repay` reverts on an overpayment.
 */
data class PayData(
    val loan: Loan,
    val index: Int,
    val dueAt: Instant,
    val unpaid: TokenAmount,
    val allowance: BigInteger,
    val late: Boolean,
    /** A DEFAULTED loan cannot be paid: the screen shows the blocked copy and no primary. */
    val blocked: Boolean,
)

data class PayUiState(
    val content: Loadable<PayData> = Loadable.Loading,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
    /** The authoritative local value of the amount to pay, from the conversion endpoint. */
    val amountLocal: LocalValue = LocalValue.Loading,
    val waitingForIndexer: Boolean = false,
    val needsWallet: Boolean = false,
)

sealed interface PayEffect {
    /** After Confirmed and the indexer wait: show the Loans tab. */
    data object OpenLoans : PayEffect
}

/**
 * S10. The amount is read from the chain (`nextInstallment`), the allowance too. When the allowance
 * is short the plan is two steps, approve for exactly that amount and then repay, each one on the
 * status sheet; when it is enough it is one. An overpayment revert reloads the installment and
 * nothing is sent again until the person confirms again.
 */
@HiltViewModel
class PayViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loans: LoanRepository,
    private val chain: ChainReadRepository,
    private val coordinator: TransactionCoordinator,
    private val wallet: WalletGateway,
    private val rates: LocalValueProvider,
    private val exchange: ExchangeRateRepository,
    private val waiter: IndexerLagWaiter,
    private val clock: Clock,
    private val sessions: SessionStore,
    prefs: PrefsStore,
) : ViewModel() {
    private val loanId: BigInteger? = savedState.get<String>(RouteArgs.LOAN_ID)?.let(LoanDetailViewModel::parseLoanId)

    private val mutableState = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<PayEffect>(Channel.BUFFERED)
    val effects: Flow<PayEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { rates.rates.collect { r -> update { copy(rates = r) } } }
        viewModelScope.launch { prefs.currency.collect { c -> update { copy(currency = c) }; convertAmount() } }
        viewModelScope.launch { rates.ensure() }
        // An overpayment revert means the installment changed under the person: read it again.
        viewModelScope.launch {
            coordinator.state.collect { tx -> if (tx is TxState.Failed && tx.refreshInstallment) load() }
        }
        load()
    }

    fun reload() {
        update { copy(content = Loadable.Loading) }
        load()
    }

    /** Confirm payment: one or two steps through the coordinator. */
    fun onConfirm() {
        val data = (mutableState.value.content as? Loadable.Success)?.data ?: return
        if (data.blocked) return
        val from = (wallet.state.value as? WalletState.Connected)?.address
        if (from == null) {
            update { copy(needsWallet = true) }
            return
        }
        update { copy(needsWallet = false) }
        val id = data.loan.loanId
        val steps = TxPlans.pay(id, data.unpaid.baseUnits, data.allowance, data.loan.amountRepaid)
        val before = data.loan.amountRepaid
        TxPlanRunner.run(
            coordinator = coordinator,
            from = from,
            steps = steps,
            action = TxAction.Payment,
            allowAmountText = data.unpaid.formatExact(java.util.Locale.getDefault()).removeSuffix(TokenAmount.SUFFIX),
            afterAllowLabel = R.string.pay_cta,
        ) { index, count ->
            TxContext(
                action = TxAction.Payment,
                stepIndex = index,
                stepCount = count,
                confirmedPrimary = TxPrimary(R.string.back_to_loans) { onBackToLoans(id, before) },
                successTitleRes = R.string.pay_success_title,
                successBodyRes = R.string.pay_success_body,
            )
        }
    }

    /** The primary of the Confirmed sheet: wait until the API shows the payment, then open Loans. */
    private fun onBackToLoans(id: BigInteger, repaidBefore: BigInteger) {
        viewModelScope.launch {
            update { copy(waitingForIndexer = true) }
            waiter.await { loans.get(id).getOrNull()?.let { it.amountRepaid > repaidBefore } ?: false }
            update { copy(waitingForIndexer = false) }
            effectChannel.trySend(PayEffect.OpenLoans)
        }
    }

    private fun load() {
        val id = loanId
        if (id == null) {
            update { copy(content = Loadable.Error(ErrorCopy.kindOf(AppException(AppError.NotFound)))) }
            return
        }
        viewModelScope.launch {
            val own = sessions.current()?.address
            val loan = loans.get(id).getOrElse { return@launch fail(it) }
            if (own == null || !loan.borrower.equals(own, ignoreCase = true)) {
                return@launch fail(AppException(AppError.NotFound))
            }
            if (loan.status == LoanStatus.DEFAULTED) {
                val data = PayData(loan, 0, Instant.EPOCH, TokenAmount(BigInteger.ZERO), BigInteger.ZERO, late = false, blocked = true)
                update { copy(content = Loadable.Success(data)) }
                return@launch
            }
            val next = chain.nextInstallment(id).getOrElse { return@launch fail(it) }
            val allowance = chain.allowance(own, Deployment.LOANBOOK_ADDRESS).getOrElse { return@launch fail(it) }
            val data = PayData(
                loan = loan,
                index = next.index,
                dueAt = next.dueAt,
                unpaid = next.unpaid,
                allowance = allowance,
                late = clock.instant().isAfter(next.dueAt),
                blocked = false,
            )
            update { copy(content = Loadable.Success(data), amountLocal = LocalValue.Loading) }
            convertAmount()
        }
    }

    private fun convertAmount() {
        val data = (mutableState.value.content as? Loadable.Success)?.data ?: return
        if (data.blocked) return
        val currency = mutableState.value.currency
        viewModelScope.launch {
            val value = exchange.convert(data.unpaid, currency).fold(
                onSuccess = { LocalValue.Value(it.value, currency, it.stale, it.asOf) },
                onFailure = { LocalValue.Unavailable },
            )
            update { copy(amountLocal = value) }
        }
    }

    private fun fail(error: Throwable) {
        update { copy(content = Loadable.Error(ErrorCopy.kindOf(error))) }
    }

    private inline fun update(change: PayUiState.() -> PayUiState) {
        mutableState.value = mutableState.value.change()
    }
}
