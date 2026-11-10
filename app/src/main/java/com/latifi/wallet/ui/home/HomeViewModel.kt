package com.latifi.wallet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.Loadable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val balance: Loadable<TokenAmount> = Loadable.Loading,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
)

sealed interface HomeEvent {
    /** The Reload button of the balance section. */
    data object ReloadBalance : HomeEvent
}

/**
 * S06. Every read uses the address of the stored session, never a route argument. A section keeps
 * its own state, so a failed read shows an error in that section only.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chain: ChainReadRepository,
    private val sessions: SessionStore,
    private val rates: LocalValueProvider,
    prefs: PrefsStore,
) : ViewModel() {
    private val balance = MutableStateFlow<Loadable<TokenAmount>>(Loadable.Loading)

    val state: StateFlow<HomeUiState> = combine(balance, rates.rates, prefs.currency) { balance, ratesState, currency ->
        HomeUiState(balance = balance, rates = ratesState, currency = currency)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        loadBalance()
        viewModelScope.launch { rates.ensure() }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.ReloadBalance -> loadBalance()
        }
    }

    private fun loadBalance() {
        balance.value = Loadable.Loading
        viewModelScope.launch {
            val address = sessions.session.first { it != null }!!.address
            balance.value = chain.tokenBalance(address).fold(
                onSuccess = { Loadable.Success(it) },
                onFailure = { Loadable.Error(ErrorCopy.kindOf(it)) },
            )
        }
    }
}
