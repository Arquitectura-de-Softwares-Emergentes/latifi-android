package com.latifi.wallet.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.feed.AmountRange
import com.latifi.wallet.domain.feed.BorrowerNameResolver
import com.latifi.wallet.domain.feed.FeedFilters
import com.latifi.wallet.domain.feed.FeedQueryMapper
import com.latifi.wallet.domain.feed.FeedSort
import com.latifi.wallet.domain.feed.Risk
import com.latifi.wallet.domain.feed.TermLimit
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.PagedLoader
import com.latifi.wallet.ui.common.PagedState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

/**
 * What a request card shows. [name] is null until the borrower's public profile answers and the
 * card then says "Name not shared yet". [isOwn] is true when the borrower is the signed-in person,
 * who cannot fund their own request.
 */
data class RequestCardModel(
    val loanId: BigInteger,
    val borrower: String,
    val name: String?,
    val amount: TokenAmount,
    val level: ReputationLevel,
    val score: Int,
    val rateBps: Int,
    val termDays: Int,
    val installments: Int,
    val isOwn: Boolean,
)

/** The body of the feed. Empty is "no open request at all", FilteredEmpty is "none under these filters". */
sealed interface FeedContent {
    data object Loading : FeedContent

    data class Error(val kind: ErrorKind) : FeedContent

    data object Empty : FeedContent

    data object FilteredEmpty : FeedContent

    data class Items(val cards: List<RequestCardModel>, val hasMore: Boolean) : FeedContent
}

/** The three filter groups, in the order the panel shows them. */
enum class FilterGroup { Risk, Term, Amount }

/** Things the screen does once and forgets: move focus, or say that something happened. */
sealed interface FeedEffect {
    /** Clear filters ran: put accessibility focus back on the result counter. */
    data object FocusCounter : FeedEffect

    /** A filter chip was removed: say "Filter removed". */
    data object FilterRemoved : FeedEffect
}

sealed interface FeedEvent {
    data class SelectRisk(val chip: Risk) : FeedEvent

    data class SelectTerm(val chip: TermLimit) : FeedEvent

    data class SelectAmount(val chip: AmountRange) : FeedEvent

    data class SetSort(val sort: FeedSort) : FeedEvent

    /** One tap on an active chip. */
    data class RemoveFilter(val group: FilterGroup) : FeedEvent

    data object ClearFilters : FeedEvent

    data object Refresh : FeedEvent

    /** The Reload button of the error panel. */
    data object Reload : FeedEvent

    data object LoadMore : FeedEvent

    data object RefreshFailedShown : FeedEvent
}

data class FeedUiState(
    val filters: FeedFilters = FeedFilters(),
    val content: FeedContent = FeedContent.Loading,
    /** `totalItems` of the latest answer for the current filters, or null while it loads. */
    val counter: Long? = null,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Grows with every new query, so the list scrolls back to the top. */
    val resetToken: Int = 0,
    /** A refresh or a next page failed while data stayed on screen: show the snackbar once. */
    val refreshFailed: Boolean = false,
) {
    /** Risk, term and amount chips that are selected. The sort never counts. */
    val activeFilterCount: Int get() = filters.activeCount

    /** True when "Clear filters" is offered. A sort change alone does not offer it. */
    val hasActiveFilters: Boolean get() = filters.hasActive

    /** The groups that have a chip, for the removable row above the counter. */
    val activeGroups: List<FilterGroup>
        get() = buildList {
            if (filters.risk != null) add(FilterGroup.Risk)
            if (filters.term != null) add(FilterGroup.Term)
            if (filters.amount != null) add(FilterGroup.Amount)
        }
}

/**
 * S12 for the lender. The query is always `status=OPEN` from [FeedQueryMapper].
 *
 * The [FeedFilters] live in the [SavedStateHandle], so they survive a detail screen and process
 * death, and they reset at sign-out because the sign-out back stack is emptied and this view model
 * dies with it. A chip tap changes the filters at once, so the chips answer immediately, but the
 * request waits for [DEBOUNCE_MS] of quiet: rapid taps send one request with the final set. A
 * response only reaches the screen when it matches the latest request (latest wins, in
 * [PagedLoader]). The counter is `null` while a request is pending or in flight, and `totalItems`
 * of the settled response afterward, so a screen reader hears the final number once.
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val loans: LoanRepository,
    profiles: ProfileRepository,
    private val rates: LocalValueProvider,
    prefs: PrefsStore,
    sessions: SessionStore,
) : ViewModel() {
    private val filters = MutableStateFlow(restoreFilters())

    /** The filters of the request that is loading or loaded, which can lag [filters] by the debounce. */
    private var applied: FeedFilters = filters.value

    /** True from a filter change until the debounced request starts. */
    private val pending = MutableStateFlow(false)
    private var debounce: Job? = null
    private val refreshFailed = MutableStateFlow(false)
    private val effectChannel = Channel<FeedEffect>(Channel.BUFFERED)

    private val paged = PagedLoader<Loan>(viewModelScope) { page ->
        loans.list(FeedQueryMapper.toQuery(applied, page = page, size = PAGE_SIZE))
    }
    private val names = BorrowerNameResolver(viewModelScope, profiles)
    private val ownAddress: StateFlow<String?> =
        sessions.session.map { it?.address?.lowercase() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** One-time effects for the screen. */
    val effects: Flow<FeedEffect> = effectChannel.receiveAsFlow()

    private val ambient = combine(rates.rates, prefs.currency, names.names, ownAddress, refreshFailed) { ratesState, currency, known, own, failed ->
        Ambient(ratesState, currency, known, own, failed)
    }

    val state: StateFlow<FeedUiState> = combine(filters, paged.state, pending, ambient) { current, list, waiting, around ->
        FeedUiState(
            filters = current,
            content = contentOf(list, around.names, around.own),
            // A number is only shown for a settled response of the latest filter set.
            counter = if (list.loaded && !waiting) list.totalItems else null,
            rates = around.rates,
            currency = around.currency,
            isRefreshing = list.isRefreshing,
            isLoadingMore = list.isLoadingMore,
            resetToken = list.resetToken,
            refreshFailed = around.refreshFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState(filters = filters.value))

    init {
        paged.reset()
        viewModelScope.launch { rates.ensure() }
        viewModelScope.launch { paged.failures.collect { refreshFailed.value = true } }
        viewModelScope.launch {
            paged.state
                .map { list -> list.items.map { it.borrower }.distinct() }
                .distinctUntilChanged()
                .collect { names.resolve(it) }
        }
    }

    fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.SelectRisk -> change { it.toggleRisk(event.chip) }
            is FeedEvent.SelectTerm -> change { it.toggleTerm(event.chip) }
            is FeedEvent.SelectAmount -> change { it.toggleAmount(event.chip) }
            is FeedEvent.SetSort -> change { it.copy(sort = event.sort) }
            is FeedEvent.RemoveFilter -> removeFilter(event.group)
            FeedEvent.ClearFilters -> clearFilters()
            FeedEvent.Refresh, FeedEvent.Reload -> refresh()
            FeedEvent.LoadMore -> paged.loadMore()
            FeedEvent.RefreshFailedShown -> refreshFailed.value = false
        }
    }

    private fun removeFilter(group: FilterGroup) {
        change {
            when (group) {
                FilterGroup.Risk -> it.copy(risk = null)
                FilterGroup.Term -> it.copy(term = null)
                FilterGroup.Amount -> it.copy(amount = null)
            }
        }
        effectChannel.trySend(FeedEffect.FilterRemoved)
    }

    /** Resets the three groups, keeps the sort and asks the screen to put focus on the counter. */
    private fun clearFilters() {
        if (!filters.value.hasActive) return
        change { it.cleared() }
        effectChannel.trySend(FeedEffect.FocusCounter)
    }

    /** Applies [transform] to the filters, saves them and schedules one request for the final set. */
    private fun change(transform: (FeedFilters) -> FeedFilters) {
        val next = transform(filters.value)
        if (next == filters.value) return
        filters.value = next
        save(next)
        pending.value = true
        debounce?.cancel()
        debounce = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val target = filters.value
            if (target != applied) {
                applied = target
                paged.reset()
            }
            pending.value = false
        }
    }

    private fun refresh() {
        // Pull to refresh also asks for fresh rates and for the names that were missing.
        viewModelScope.launch { rates.refresh() }
        names.retryMissing()
        names.resolve(paged.state.value.items.map { it.borrower })
        paged.refresh()
    }

    private fun save(value: FeedFilters) {
        savedState[KEY_RISK] = value.risk?.name
        savedState[KEY_TERM] = value.term?.name
        savedState[KEY_AMOUNT] = value.amount?.name
        savedState[KEY_SORT] = value.sort.name
    }

    private fun restoreFilters(): FeedFilters = FeedFilters(
        risk = Risk.entries.firstOrNull { it.name == savedState.get<String>(KEY_RISK) },
        term = TermLimit.entries.firstOrNull { it.name == savedState.get<String>(KEY_TERM) },
        amount = AmountRange.entries.firstOrNull { it.name == savedState.get<String>(KEY_AMOUNT) },
        sort = FeedSort.entries.firstOrNull { it.name == savedState.get<String>(KEY_SORT) } ?: FeedSort.Reputation,
    )

    private data class Ambient(
        val rates: RatesState,
        val currency: LocalCurrency,
        val names: Map<String, String>,
        val own: String?,
        val refreshFailed: Boolean,
    )

    private fun contentOf(list: PagedState<Loan>, known: Map<String, String>, own: String?): FeedContent =
        when {
            list.error != null -> FeedContent.Error(list.error)
            !list.loaded -> FeedContent.Loading
            list.items.isEmpty() -> if (applied.hasActive) FeedContent.FilteredEmpty else FeedContent.Empty
            else -> FeedContent.Items(list.items.map { it.toCardModel(known[it.borrower.lowercase()], own) }, list.hasMore)
        }

    companion object {
        const val PAGE_SIZE = LoanQuery.DEFAULT_SIZE

        /** Quiet time after the last chip tap before the request goes out. */
        const val DEBOUNCE_MS = 400L

        private const val KEY_RISK = "feed_risk"
        private const val KEY_TERM = "feed_term"
        private const val KEY_AMOUNT = "feed_amount"
        private const val KEY_SORT = "feed_sort"
    }
}

private fun Loan.toCardModel(name: String?, own: String?) = RequestCardModel(
    loanId = loanId,
    borrower = borrower,
    name = name,
    amount = TokenAmount(principal),
    level = borrowerReputation.level,
    score = borrowerReputation.score,
    rateBps = interestRateBps,
    termDays = termDays,
    installments = installments,
    isOwn = own != null && borrower.equals(own, ignoreCase = true),
)
