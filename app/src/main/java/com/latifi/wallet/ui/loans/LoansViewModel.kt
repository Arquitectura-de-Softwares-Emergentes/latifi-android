package com.latifi.wallet.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.Loadable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoansUiState(
    val content: Loadable<List<LoanCardModel>> = Loadable.Loading,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
)

/** S07 for the borrower. Loads page 0 of the signed-in borrower's loans, newest request first. */
@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loans: LoanRepository,
    private val rates: LocalValueProvider,
    private val prefs: PrefsStore,
    private val sessions: SessionStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoansUiState())
    val state: StateFlow<LoansUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { rates.rates.collect { r -> mutableState.value = mutableState.value.copy(rates = r) } }
        viewModelScope.launch { prefs.currency.collect { c -> mutableState.value = mutableState.value.copy(currency = c) } }
        viewModelScope.launch { rates.ensure() }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val address = sessions.session.first { it != null }!!.address
        val query = LoanQuery(borrower = address, sort = LoanSort.Recent, size = PAGE_SIZE)
        mutableState.value = mutableState.value.copy(
            content = loans.list(query).fold(
                onSuccess = { page -> Loadable.Success(page.items.map { it.toCardModel() }) },
                onFailure = { Loadable.Error(ErrorCopy.kindOf(it)) },
            ),
        )
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
