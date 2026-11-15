package com.latifi.wallet.ui.fund

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
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.prefs.PrefsStore
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
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.lender.RequestDetail
import com.latifi.wallet.ui.lender.RequestDetailViewModel
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
import javax.inject.Inject

/** What S14 shows: the summary numbers and the allowance the lender has now. */
data class FundData(val detail: RequestDetail, val allowance: BigInteger)

/**
 * [notOpen] is true when the request is no longer OPEN: the screen shows the failure copy with
 * "Back to requests" and no way to sign. [ownRequest] is the refused case that plan 05-10 already
 * keeps from reaching this screen.
 */
data class FundUiState(
    val content: Loadable<FundData> = Loadable.Loading,
    val notOpen: Boolean = false,
    val ownRequest: Boolean = false,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
    val waitingForIndexer: Boolean = false,
    val needsWallet: Boolean = false,
)

sealed interface FundEffect {
    /** Show the Invest tab again (the request is gone). */
    data object OpenRequests : FundEffect

    /** After Confirmed and the indexer wait: show the Portfolio tab. */
    data object OpenPortfolio : FundEffect
}

/**
 * S14. The primary approves exactly the principal when the allowance is short and then funds, each
 * step on the status sheet. A request that is not OPEN, or the lender's own, never reaches the
 * wallet. After Confirmed the primary waits for the indexer to show the loan as ACTIVE.
 */
@HiltViewModel
class FundViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loans: LoanRepository,
    private val chain: ChainReadRepository,
    private val coordinator: TransactionCoordinator,
    private val wallet: WalletGateway,
    private val rates: LocalValueProvider,
    private val waiter: IndexerLagWaiter,
    private val sessions: SessionStore,
    prefs: PrefsStore,
) : ViewModel() {
    private val loanId: BigInteger? = savedState.get<String>(RouteArgs.LOAN_ID)?.let(LoanDetailViewModel::parseLoanId)

    private val mutableState = MutableStateFlow(FundUiState())
    val state: StateFlow<FundUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<FundEffect>(Channel.BUFFERED)
    val effects: Flow<FundEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { rates.rates.collect { r -> update { copy(rates = r) } } }
        viewModelScope.launch { prefs.currency.collect { c -> update { copy(currency = c) } } }
        viewModelScope.launch { rates.ensure() }
        load()
    }

    fun reload() {
        update { copy(content = Loadable.Loading) }
        load()
    }

    /** Confirm funding. */
    fun onConfirm() {
        val data = (mutableState.value.content as? Loadable.Success)?.data ?: return
        val state = mutableState.value
        if (state.notOpen || state.ownRequest) return
        val from = (wallet.state.value as? WalletState.Connected)?.address
        if (from == null) {
            update { copy(needsWallet = true) }
            return
        }
        update { copy(needsWallet = false) }
        val loan = data.detail.loan
        val steps = TxPlans.fund(loan.loanId, loan.principal, data.allowance)
        TxPlanRunner.run(
            coordinator = coordinator,
            from = from,
            steps = steps,
            action = TxAction.Funding,
            allowAmountText = TokenAmount(loan.principal).formatExact(java.util.Locale.getDefault()).removeSuffix(TokenAmount.SUFFIX),
            afterAllowLabel = R.string.fund_cta,
        ) { index, count ->
            TxContext(
                action = TxAction.Funding,
                stepIndex = index,
                stepCount = count,
                confirmedPrimary = TxPrimary(R.string.fund_view_portfolio) { onViewPortfolio(loan.loanId) },
                successTitleRes = R.string.fund_success_title,
                successBodyRes = R.string.fund_success_body,
                onBackToRequests = { effectChannel.trySend(FundEffect.OpenRequests) },
            )
        }
    }

    /** The "Back to requests" primary of the not open state on the screen itself. */
    fun onBackToRequests() {
        effectChannel.trySend(FundEffect.OpenRequests)
    }

    private fun onViewPortfolio(id: BigInteger) {
        viewModelScope.launch {
            update { copy(waitingForIndexer = true) }
            waiter.await { loans.get(id).getOrNull()?.status == LoanStatus.ACTIVE }
            update { copy(waitingForIndexer = false) }
            effectChannel.trySend(FundEffect.OpenPortfolio)
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
            val ownRequest = own != null && loan.borrower.equals(own, ignoreCase = true)
            val allowance = if (own == null) {
                BigInteger.ZERO
            } else {
                chain.allowance(own, Deployment.LOANBOOK_ADDRESS).getOrElse { return@launch fail(it) }
            }
            update {
                copy(
                    content = Loadable.Success(FundData(RequestDetailViewModel.detailOf(loan), allowance)),
                    notOpen = loan.status != LoanStatus.OPEN,
                    ownRequest = ownRequest,
                )
            }
        }
    }

    private fun fail(error: Throwable) {
        update { copy(content = Loadable.Error(ErrorCopy.kindOf(error))) }
    }

    private inline fun update(change: FundUiState.() -> FundUiState) {
        mutableState.value = mutableState.value.change()
    }
}
