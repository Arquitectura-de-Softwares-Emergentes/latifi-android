package com.latifi.wallet.ui.loans

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The five chips of S07 and the API status each one asks for. All asks for no status. */
enum class LoanFilter(val status: LoanStatus?) {
    All(null),
    Open(LoanStatus.OPEN),
    Pending(LoanStatus.ACTIVE),
    Paid(LoanStatus.REPAID),
    Overdue(LoanStatus.DEFAULTED),
}

/** The body of the Loans screen. Empty and FilteredEmpty are different states with different copy. */
sealed interface LoansContent {
    data object Loading : LoansContent

    data class Error(val kind: ErrorKind) : LoansContent

    /** The borrower has no loans at all. */
    data object Empty : LoansContent

    /** A status is selected and no loan has it. */
    data object FilteredEmpty : LoansContent

    data class Items(val cards: List<LoanCardModel>, val hasMore: Boolean) : LoansContent
}

data class LoansUiState(
    val filter: LoanFilter = LoanFilter.All,
    val content: LoansContent = LoansContent.Loading,
    /** `totalItems` of the latest answer for the selected filter, or null while it loads. */
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

sealed interface LoansEvent {
    data class SelectFilter(val filter: LoanFilter) : LoansEvent

    data object Refresh : LoansEvent

    data object LoadMore : LoansEvent

    /** The Reload button of the error panel. */
    data object Reload : LoansEvent

    data object ShowAll : LoansEvent

    data object RefreshFailedShown : LoansEvent
}

/**
 * S07 for the borrower. The status filter lives in the [SavedStateHandle], so it survives a
 * detail screen and process death, and it resets at sign-out because the sign-out back stack is
 * emptied and this view model dies with it. The query always carries the signed-in borrower, the
 * `recent` sort and 20 items per page. The counter comes from the latest answer only.
 */
@HiltViewModel
class LoansViewModel @Inject constructor(
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
                borrower = address,
                sort = LoanSort.Recent,
                page = page,
                size = PAGE_SIZE,
            ),
        )
    }

    val state: StateFlow<LoansUiState> = combine(
        filter,
        paged.state,
        rates.rates,
        prefs.currency,
        refreshFailed,
    ) { filter, list, ratesState, currency, failed ->
        LoansUiState(
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoansUiState(filter = filter.value))

    init {
        paged.reset()
        viewModelScope.launch { rates.ensure() }
        viewModelScope.launch { paged.failures.collect { refreshFailed.value = true } }
    }

    fun onEvent(event: LoansEvent) {
        when (event) {
            is LoansEvent.SelectFilter -> select(event.filter)
            LoansEvent.ShowAll -> select(LoanFilter.All)
            LoansEvent.Refresh, LoansEvent.Reload -> refresh()
            LoansEvent.LoadMore -> paged.loadMore()
            LoansEvent.RefreshFailedShown -> refreshFailed.value = false
        }
    }

    private fun select(selected: LoanFilter) {
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

    private fun restoreFilter(): LoanFilter =
        LoanFilter.entries.firstOrNull { it.name == savedState.get<String>(KEY_FILTER) } ?: LoanFilter.All

    private fun contentOf(filter: LoanFilter, list: com.latifi.wallet.ui.common.PagedState<Loan>): LoansContent =
        when {
            list.error != null -> LoansContent.Error(list.error)
            !list.loaded -> LoansContent.Loading
            list.items.isEmpty() -> if (filter == LoanFilter.All) LoansContent.Empty else LoansContent.FilteredEmpty
            else -> LoansContent.Items(list.items.map { it.toCardModel() }, list.hasMore)
        }

    companion object {
        const val PAGE_SIZE = 20
        private const val KEY_FILTER = "loans_filter"
    }
}
