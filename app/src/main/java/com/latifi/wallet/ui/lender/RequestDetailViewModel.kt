package com.latifi.wallet.ui.lender

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileGate
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.reputation.ReputationRepository
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.loans.LoanDetailViewModel
import com.latifi.wallet.ui.nav.RouteArgs
import com.latifi.wallet.ui.profile.ProfileViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

/** What the Fund request primary of S13 does for a loan. */
enum class FundAction {
    /** OPEN and not the lender's own request: the primary is enabled. */
    Available,

    /** OPEN but the borrower is the signed-in person: the primary is disabled with its reason. */
    OwnRequest,

    /** Any other status, or nothing loaded yet: there is no primary. The status chip says why. */
    None,
}

/** The numbers S13 derives from the loan. */
data class RequestDetail(
    val loan: Loan,
    /** The stored total of a funded loan, or the contract formula for an open request. */
    val totalToRepay: BigInteger,
    /** The first element of the contract split: the amount of every installment but the last. */
    val installmentAmount: BigInteger,
    /** What the lender earns: the total to repay minus the principal. */
    val earnings: BigInteger,
)

/** The recent payment history block. It loads on its own, so a slow reputation call never hides the loan. */
sealed interface RecentHistory {
    data object Loading : RecentHistory

    data class Error(val kind: ErrorKind) : RecentHistory

    /** A borrower with no reputation or no event yet: not an error. */
    data object NewBorrower : RecentHistory

    data class Items(val events: List<ReputationEvent>) : RecentHistory
}

data class RequestDetailUiState(
    val content: Loadable<RequestDetail> = Loadable.Loading,
    /** The borrower's public name once `publicName` answers, else null. */
    val borrowerName: String? = null,
    /** `0x12ab...9f3c`, shown until a name arrives and whenever there is none. */
    val borrowerShortAddress: String? = null,
    val history: RecentHistory = RecentHistory.Loading,
    /** The signed-in address in lowercase, or null while the session loads. */
    val ownAddress: String? = null,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
) {
    /** The Fund request primary. The screen never offers an action the contract would reject. */
    val fundAction: FundAction
        get() {
            val loan = (content as? Loadable.Success)?.data?.loan ?: return FundAction.None
            return when {
                loan.status != LoanStatus.OPEN -> FundAction.None
                ownAddress != null && loan.borrower.equals(ownAddress, ignoreCase = true) -> FundAction.OwnRequest
                else -> FundAction.Available
            }
        }
}

/**
 * S13. Reads `loanId` from the route as digits only: anything else, or an id the repository does
 * not know, shows the not found copy and never reaches a money movement. The loan shows first.
 * Then the borrower name and the last five reputation events load side by side with independent
 * states, so a slow profile call never blocks the numbers. A borrower without reputation is the
 * "new borrower" state, not an error.
 *
 * [gate] and [sessions] are handed to the screen route, which wraps the Fund request primary in
 * `rememberRequireProfile`: the funding flow starts only after the profile gate passes.
 */
@HiltViewModel
class RequestDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loans: LoanRepository,
    private val profiles: ProfileRepository,
    private val reputation: ReputationRepository,
    private val rates: LocalValueProvider,
    prefs: PrefsStore,
    val sessions: SessionStore,
    val gate: ProfileGate,
) : ViewModel() {
    private val loanId: BigInteger? = savedState.get<String>(RouteArgs.LOAN_ID)?.let(LoanDetailViewModel::parseLoanId)

    private val mutableState = MutableStateFlow(RequestDetailUiState())
    val state: StateFlow<RequestDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { rates.rates.collect { r -> update { copy(rates = r) } } }
        viewModelScope.launch { prefs.currency.collect { c -> update { copy(currency = c) } } }
        viewModelScope.launch { sessions.session.collect { s -> update { copy(ownAddress = s?.address?.lowercase()) } } }
        viewModelScope.launch { rates.ensure() }
        load()
    }

    /** The Reload button of the error panel: everything again, with fresh rates. */
    fun reload() {
        update { copy(content = Loadable.Loading, history = RecentHistory.Loading, borrowerName = null) }
        viewModelScope.launch { rates.refresh() }
        load()
    }

    /** The Reload button of the history block alone. */
    fun reloadHistory() {
        val loan = (mutableState.value.content as? Loadable.Success)?.data?.loan ?: return
        update { copy(history = RecentHistory.Loading) }
        viewModelScope.launch { loadHistory(loan.borrower) }
    }

    private fun load() {
        val id = loanId
        if (id == null) {
            update { copy(content = Loadable.Error(ErrorKind.NotFound)) }
            return
        }
        viewModelScope.launch {
            loans.get(id).fold(
                onSuccess = { loan ->
                    update {
                        copy(
                            content = Loadable.Success(detailOf(loan)),
                            borrowerShortAddress = ProfileViewModel.shorten(loan.borrower),
                        )
                    }
                    // Two independent reads. Each one fills its own part of the state.
                    launch { loadName(loan.borrower) }
                    launch { loadHistory(loan.borrower) }
                },
                onFailure = { error -> update { copy(content = Loadable.Error(ErrorCopy.kindOf(error))) } },
            )
        }
    }

    private suspend fun loadName(borrower: String) {
        val name = profiles.publicName(borrower)
        if (name != null) update { copy(borrowerName = name) }
    }

    private suspend fun loadHistory(borrower: String) {
        reputation.history(borrower, null, 0, HISTORY_SIZE).fold(
            onSuccess = { page ->
                update { copy(history = if (page.items.isEmpty()) RecentHistory.NewBorrower else RecentHistory.Items(page.items)) }
            },
            onFailure = { error ->
                val notFound = (error as? AppException)?.error == AppError.ReputationNotFound
                update { copy(history = if (notFound) RecentHistory.NewBorrower else RecentHistory.Error(ErrorCopy.kindOf(error))) }
            },
        )
    }

    private inline fun update(change: RequestDetailUiState.() -> RequestDetailUiState) {
        mutableState.value = mutableState.value.change()
    }

    companion object {
        /** The request details show the last five events of the borrower. */
        const val HISTORY_SIZE = 5

        /**
         * The numbers the contract will use. An open request reports a total due of zero, so the
         * formula of `LoanMath` stands in. The installment amount is the first contract split
         * element and the lender earns the total minus the principal.
         */
        fun detailOf(loan: Loan): RequestDetail {
            val total = if (loan.totalDue.signum() > 0) {
                loan.totalDue
            } else {
                LoanMath.totalDue(loan.principal, loan.interestRateBps, loan.termDays)
            }
            return RequestDetail(
                loan = loan,
                totalToRepay = total,
                installmentAmount = LoanMath.installmentAmounts(total, loan.installments).first(),
                earnings = total.subtract(loan.principal),
            )
        }
    }
}
