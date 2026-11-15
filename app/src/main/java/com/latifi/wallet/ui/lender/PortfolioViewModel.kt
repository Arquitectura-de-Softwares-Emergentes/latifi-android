package com.latifi.wallet.ui.lender

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.PagedLoader
import com.latifi.wallet.ui.common.PagedState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The four chips of S15 and the API status each one asks for. All asks for no status. A lender
 * never has an OPEN loan, because an open request has no lender yet, so there is no Open chip.
 */
enum class PortfolioFilter(val status: LoanStatus?) {
    All(null),
    Pending(LoanStatus.ACTIVE),
    Paid(LoanStatus.REPAID),
    Overdue(LoanStatus.DEFAULTED),
}

/** The body of the Portfolio screen. Empty and FilteredEmpty are different states with different copy. */
sealed interface PortfolioContent {
    data object Loading : PortfolioContent

    data class Error(val kind: ErrorKind) : PortfolioContent

    /** The lender has not funded any request. */
    data object Empty : PortfolioContent

    /** A status is selected and no investment has it. */
    data object FilteredEmpty : PortfolioContent

    data class Items(val cards: List<InvestmentCardModel>, val hasMore: Boolean) : PortfolioContent
}

data class PortfolioUiState(
    val filter: PortfolioFilter = PortfolioFilter.All,
    val content: PortfolioContent = PortfolioContent.Loading,
    /** `totalItems` of the latest answer for the selected chip, or null while it loads. */
    val counter: Long? = null,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Grows with every filter change, so the list scrolls back to the top. */
    val resetToken: Int = 0,
    /** A refresh or a next page failed while data stayed on screen: show the snackbar once. */
    val refreshFailed: Boolean = false,
)

sealed interface PortfolioEvent {
    data class SelectFilter(val filter: PortfolioFilter) : PortfolioEvent

    data object Refresh : PortfolioEvent

    data object LoadMore : PortfolioEvent

    /** The Reload button of the error panel. */
    data object Reload : PortfolioEvent

    data object ShowAll : PortfolioEvent

    data object RefreshFailedShown : PortfolioEvent
}

/**
 * S15 for the lender. It mirrors the borrower's Loans list: the status chip lives in the
 * [SavedStateHandle], so it survives a detail screen and process death, and it resets at sign-out
 * because the sign-out back stack is emptied and this view model dies with it. The query always
 * carries the signed-in lender, the `nextDue` sort and 20 items per page. The counter comes from
 * the latest answer only.
 */
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val loans: LoanRepository,
    private val rates: LocalValueProvider,
    prefs: PrefsStore,
    private val sessions: SessionStore,
) : ViewModel() {
    private val filter = MutableStateFlow(restoreFilter())
    private val refreshFailed = MutableStateFlow(false)

    private val paged = PagedLoader<Loan>(viewModelScope) { page ->
        val address = sessions.session.first { it != null }!!.address
        loans.list(
            LoanQuery(
                status = filter.value.status,
                lender = address,
                sort = LoanSort.NextDue,
                page = page,
                size = PAGE_SIZE,
            ),
        )
    }

    val state: StateFlow<PortfolioUiState> = combine(
        filter,
        paged.state,
        rates.rates,
        prefs.currency,
        refreshFailed,
    ) { filter, list, ratesState, currency, failed ->
        PortfolioUiState(
            filter = filter,
            content = contentOf(filter, list),
            counter = if (list.loaded) list.totalItems else null,
            rates = ratesState,
            currency = currency,
            isRefreshing = list.isRefreshing,
            isLoadingMore = list.isLoadingMore,
            resetToken = list.resetToken,
            refreshFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PortfolioUiState(filter = filter.value))

    init {
        paged.reset()
        viewModelScope.launch { rates.ensure() }
        viewModelScope.launch { paged.failures.collect { refreshFailed.value = true } }
    }

    fun onEvent(event: PortfolioEvent) {
        when (event) {
            is PortfolioEvent.SelectFilter -> select(event.filter)
            PortfolioEvent.ShowAll -> select(PortfolioFilter.All)
            PortfolioEvent.Refresh, PortfolioEvent.Reload -> refresh()
            PortfolioEvent.LoadMore -> paged.loadMore()
            PortfolioEvent.RefreshFailedShown -> refreshFailed.value = false
        }
    }

    private fun select(selected: PortfolioFilter) {
        if (selected == filter.value) return
        filter.value = selected
        savedState[KEY_FILTER] = selected.name
        paged.reset()
    }

    private fun refresh() {
        // Pull to refresh also asks for fresh rates, which the provider caches for 5 minutes otherwise.
        viewModelScope.launch { rates.refresh() }
        paged.refresh()
    }

    private fun restoreFilter(): PortfolioFilter =
        PortfolioFilter.entries.firstOrNull { it.name == savedState.get<String>(KEY_FILTER) } ?: PortfolioFilter.All

    private fun contentOf(filter: PortfolioFilter, list: PagedState<Loan>): PortfolioContent =
        when {
            list.error != null -> PortfolioContent.Error(list.error)
            !list.loaded -> PortfolioContent.Loading
            list.items.isEmpty() -> if (filter == PortfolioFilter.All) PortfolioContent.Empty else PortfolioContent.FilteredEmpty
            else -> PortfolioContent.Items(list.items.map { it.toInvestmentModel() }, list.hasMore)
        }

    companion object {
        const val PAGE_SIZE = 20
        private const val KEY_FILTER = "portfolio_filter"
    }
}
