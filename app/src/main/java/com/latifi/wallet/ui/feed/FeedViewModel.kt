package com.latifi.wallet.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.feed.BorrowerNameResolver
import com.latifi.wallet.domain.feed.FeedFilters
import com.latifi.wallet.domain.feed.FeedQueryMapper
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
)

/**
 * S12 for the lender. The query is always `status=OPEN` from [FeedQueryMapper]; the tracer slice
 * loads the default one: sort by reputation, page 0, 20 per page. The counter is `totalItems` of
 * the answer, never the size of the loaded page.
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
    private val filters = FeedFilters()

    private val paged = PagedLoader<Loan>(viewModelScope) { page ->
        loans.list(FeedQueryMapper.toQuery(filters, page = page, size = PAGE_SIZE))
    }
    private val names = BorrowerNameResolver(viewModelScope, profiles)
    private val ownAddress: StateFlow<String?> =
        sessions.session.map { it?.address?.lowercase() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<FeedUiState> = combine(
        paged.state,
        rates.rates,
        prefs.currency,
        names.names,
        ownAddress,
    ) { list, ratesState, currency, known, own ->
        FeedUiState(
            filters = filters,
            content = contentOf(list, known, own),
            counter = if (list.loaded) list.totalItems else null,
            rates = ratesState,
            currency = currency,
            isRefreshing = list.isRefreshing,
            isLoadingMore = list.isLoadingMore,
            resetToken = list.resetToken,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState(filters = filters))

    init {
        paged.reset()
        viewModelScope.launch { rates.ensure() }
        viewModelScope.launch {
            paged.state
                .map { list -> list.items.map { it.borrower }.distinct() }
                .distinctUntilChanged()
                .collect { names.resolve(it) }
        }
    }

    private fun contentOf(list: PagedState<Loan>, known: Map<String, String>, own: String?): FeedContent =
        when {
            list.error != null -> FeedContent.Error(list.error)
            !list.loaded -> FeedContent.Loading
            list.items.isEmpty() -> FeedContent.Empty
            else -> FeedContent.Items(list.items.map { it.toCardModel(known[it.borrower.lowercase()], own) }, list.hasMore)
        }

    companion object {
        const val PAGE_SIZE = LoanQuery.DEFAULT_SIZE
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
