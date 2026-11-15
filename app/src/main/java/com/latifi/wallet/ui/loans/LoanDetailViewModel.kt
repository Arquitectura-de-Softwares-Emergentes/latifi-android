package com.latifi.wallet.ui.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.model.InstallmentEntry
import com.latifi.wallet.domain.model.InstallmentSchedule
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.nav.RouteArgs
import com.latifi.wallet.ui.profile.ProfileViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

/** The one main thing S08 offers for a loan, decided by its status. */
enum class LoanAction {
    /** ACTIVE: the Pay installment button. */
    Pay,

    /** OPEN: no button, the text "Waiting for a lender". */
    WaitingForLender,

    /** REPAID: a success note. */
    Repaid,

    /** DEFAULTED: a disabled button with the reason. */
    Blocked,
}

data class LoanDetail(
    val loan: Loan,
    val schedule: List<InstallmentEntry>,
    val action: LoanAction,
    /** The stored total of a funded loan, or the contract formula for an open request. */
    val totalToRepay: BigInteger,
    /** The amount of every installment but the last, which absorbs the remainder. */
    val installmentAmount: BigInteger,
)

data class LoanDetailUiState(
    val content: Loadable<LoanDetail> = Loadable.Loading,
    /** The lender's public name once `publicName` answers, else null. */
    val lenderName: String? = null,
    /** `0x12ab...9f3c`, shown until a name arrives and whenever there is none. */
    val lenderShortAddress: String? = null,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
)

/**
 * S08. Reads `loanId` from the route as digits only: anything else, or an id the repository does
 * not know, shows the not found copy and never reaches a money movement. The lender name is looked
 * up after the loan shows, so a slow profile call never blocks the screen.
 */
@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loans: LoanRepository,
    private val profiles: ProfileRepository,
    private val rates: LocalValueProvider,
    prefs: PrefsStore,
) : ViewModel() {
    private val loanId: BigInteger? = savedState.get<String>(RouteArgs.LOAN_ID)?.let(::parseLoanId)

    private val mutableState = MutableStateFlow(LoanDetailUiState())
    val state: StateFlow<LoanDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { rates.rates.collect { r -> update { copy(rates = r) } } }
        viewModelScope.launch { prefs.currency.collect { c -> update { copy(currency = c) } } }
        viewModelScope.launch { rates.ensure() }
        load()
    }

    /** The Reload button. */
    fun reload() {
        update { copy(content = Loadable.Loading) }
        viewModelScope.launch { rates.refresh() }
        load()
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
                            lenderShortAddress = loan.lender?.let { ProfileViewModel.shorten(it) },
                        )
                    }
                    loan.lender?.let { lender ->
                        val name = profiles.publicName(lender)
                        if (name != null) update { copy(lenderName = name) }
                    }
                },
                onFailure = { error -> update { copy(content = Loadable.Error(ErrorCopy.kindOf(error))) } },
            )
        }
    }

    private inline fun update(change: LoanDetailUiState.() -> LoanDetailUiState) {
        mutableState.value = mutableState.value.change()
    }

    companion object {
        private val DIGITS = Regex("[0-9]{1,78}")

        /** A loan id is an unsigned 256 bit number, so it is at most 78 decimal digits. */
        fun parseLoanId(text: String): BigInteger? = if (DIGITS.matches(text)) BigInteger(text) else null

        fun actionOf(status: LoanStatus): LoanAction = when (status) {
            LoanStatus.ACTIVE -> LoanAction.Pay
            LoanStatus.OPEN -> LoanAction.WaitingForLender
            LoanStatus.REPAID -> LoanAction.Repaid
            LoanStatus.DEFAULTED -> LoanAction.Blocked
        }

        fun detailOf(loan: Loan): LoanDetail {
            val total = if (loan.totalDue.signum() > 0) {
                loan.totalDue
            } else {
                LoanMath.totalDue(loan.principal, loan.interestRateBps, loan.termDays)
            }
            return LoanDetail(
                loan = loan,
                schedule = InstallmentSchedule.build(loan),
                action = actionOf(loan.status),
                totalToRepay = total,
                installmentAmount = LoanMath.installmentAmounts(total, loan.installments).first(),
            )
        }
    }
}
