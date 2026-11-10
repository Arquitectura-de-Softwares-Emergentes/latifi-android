package com.latifi.wallet.ui.lender

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.model.InstallmentEntry
import com.latifi.wallet.domain.model.InstallmentSchedule
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
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

/**
 * What S16 shows for one investment. There is no action: the screen only reports. The status is
 * the one the API reports, so an active loan with a past due date stays Pending until the API says
 * DEFAULTED.
 */
data class InvestmentDetail(
    val loan: Loan,
    val schedule: List<InstallmentEntry>,
    /** The stored total of the loan: what the lender gets back in all. */
    val expectedReturn: BigInteger,
)

data class InvestmentDetailUiState(
    val content: Loadable<InvestmentDetail> = Loadable.Loading,
    /** The borrower's public name once `publicName` answers, else null. */
    val borrowerName: String? = null,
    /** `0x12ab...9f3c`, shown until a name arrives and whenever there is none. */
    val borrowerShortAddress: String? = null,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
)

/**
 * S16. Reads `loanId` from the route as digits only: anything else, or an id the repository does
 * not know, shows the not found copy. The borrower name is looked up after the loan shows, so a slow
 * profile call never blocks the screen.
 */
@HiltViewModel
class InvestmentDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loans: LoanRepository,
    private val profiles: ProfileRepository,
    private val rates: LocalValueProvider,
    prefs: PrefsStore,
) : ViewModel() {
    private val loanId: BigInteger? = savedState.get<String>(RouteArgs.LOAN_ID)?.let(LoanDetailViewModel::parseLoanId)

    private val mutableState = MutableStateFlow(InvestmentDetailUiState())
    val state: StateFlow<InvestmentDetailUiState> = mutableState.asStateFlow()

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
                            borrowerShortAddress = ProfileViewModel.shorten(loan.borrower),
                        )
                    }
                    val name = profiles.publicName(loan.borrower)
                    if (name != null) update { copy(borrowerName = name) }
                },
                onFailure = { error -> update { copy(content = Loadable.Error(ErrorCopy.kindOf(error))) } },
            )
        }
    }

    private inline fun update(change: InvestmentDetailUiState.() -> InvestmentDetailUiState) {
        mutableState.value = mutableState.value.change()
    }

    companion object {
        fun detailOf(loan: Loan): InvestmentDetail = InvestmentDetail(
            loan = loan,
            schedule = InstallmentSchedule.build(loan),
            expectedReturn = expectedReturn(loan),
        )
    }
}
