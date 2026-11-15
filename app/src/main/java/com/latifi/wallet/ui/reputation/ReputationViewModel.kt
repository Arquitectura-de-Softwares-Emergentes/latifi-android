package com.latifi.wallet.ui.reputation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationSummary
import com.latifi.wallet.domain.reputation.ReputationRepository
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.PagedLoader
import com.latifi.wallet.ui.common.PagedState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The four chips of the history and the event type each one asks for. All asks for no type, which
 * is the only filter that returns `PROFILE_COMPLETED`.
 */
enum class HistoryFilter(val type: ReputationEventType?) {
    All(null),
    OnTime(ReputationEventType.REPAID_ON_TIME),
    Late(ReputationEventType.REPAID_LATE),
    Defaulted(ReputationEventType.DEFAULTED),
}

/** The score block of S11. [NoProfile] is the 404 REPUTATION_NOT_FOUND state. */
sealed interface ScoreContent {
    data object Loading : ScoreContent

    data class Error(val kind: ErrorKind) : ScoreContent

    /** The person has not added their details yet, so there is no reputation to show. */
    data object NoProfile : ScoreContent

    data class Ready(val summary: ReputationSummary) : ScoreContent
}

/** The history list under the score. */
sealed interface HistoryContent {
    data object Loading : HistoryContent

    data class Error(val kind: ErrorKind) : HistoryContent

    /** No event at all (filter All). */
    data object Empty : HistoryContent

    /** A type filter is on and no event has it. */
    data object FilteredEmpty : HistoryContent

    data class Items(val events: List<ReputationEvent>, val hasMore: Boolean) : HistoryContent
}

data class ReputationUiState(
    val score: ScoreContent = ScoreContent.Loading,
    val filter: HistoryFilter = HistoryFilter.All,
    val history: HistoryContent = HistoryContent.Loading,
    /** `totalItems` of the latest answer for the selected filter, or null while it loads. */
    val counter: Long? = null,
    val isLoadingMore: Boolean = false,
    /** A next page failed while rows stayed on screen: show the snackbar once. */
    val refreshFailed: Boolean = false,
)

sealed interface ReputationEvents {
    data class SelectFilter(val filter: HistoryFilter) : ReputationEvents

    /** Reload the score and then the history. Used by the Reload button of the score block. */
    data object Reload : ReputationEvents

    /** Reload the history only. Used by the Reload button of the history error panel. */
    data object ReloadHistory : ReputationEvents

    data object LoadMore : ReputationEvents

    data object ShowAll : ReputationEvents

    data object RefreshFailedShown : ReputationEvents

    /**
     * The tab came back to the front. A person who saw "add your details" may have just saved
     * them, so this reloads when (and only when) the score block shows that state.
     */
    data object Resumed : ReputationEvents
}

/**
 * S11 for the borrower. The score loads first, and the history loads once the score is there, so
 * a person with no profile never triggers a history call that would fail the same way. The chip
 * lives in the [SavedStateHandle] and becomes the `type` of the history call. Every read uses the
 * address of the stored session. The counter comes from the latest answer only, because
 * [PagedLoader] drops answers of an older query.
 */
@HiltViewModel
class ReputationViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val reputation: ReputationRepository,
    private val sessions: SessionStore,
) : ViewModel() {
    private val filter = MutableStateFlow(restoreFilter())
    private val score = MutableStateFlow<ScoreContent>(ScoreContent.Loading)
    private val refreshFailed = MutableStateFlow(false)
    private var scoreJob: Job? = null

    private val paged = PagedLoader<ReputationEvent>(viewModelScope) { page ->
        val address = sessions.session.first { it != null }!!.address
        reputation.history(address, filter.value.type, page, PAGE_SIZE)
    }

    val state: StateFlow<ReputationUiState> = combine(filter, score, paged.state, refreshFailed) { filter, score, list, failed ->
        ReputationUiState(
            score = score,
            filter = filter,
            history = historyOf(filter, score, list),
            counter = if (score is ScoreContent.Ready && list.loaded) list.totalItems else null,
            isLoadingMore = list.isLoadingMore,
            refreshFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReputationUiState(filter = filter.value))

    init {
        loadScore()
        viewModelScope.launch { paged.failures.collect { refreshFailed.value = true } }
    }

    fun onEvent(event: ReputationEvents) {
        when (event) {
            is ReputationEvents.SelectFilter -> select(event.filter)
            ReputationEvents.ShowAll -> select(HistoryFilter.All)
            ReputationEvents.Reload -> loadScore()
            ReputationEvents.ReloadHistory -> paged.refresh()
            ReputationEvents.LoadMore -> paged.loadMore()
            ReputationEvents.RefreshFailedShown -> refreshFailed.value = false
            ReputationEvents.Resumed -> if (score.value is ScoreContent.NoProfile) loadScore()
        }
    }

    private fun select(selected: HistoryFilter) {
        if (selected == filter.value) return
        filter.value = selected
        savedState[KEY_FILTER] = selected.name
        // Without a score there is no history to filter, so nothing loads until the score arrives.
        if (score.value is ScoreContent.Ready) paged.reset()
    }

    private fun loadScore() {
        scoreJob?.cancel()
        score.value = ScoreContent.Loading
        scoreJob = viewModelScope.launch {
            val address = sessions.session.first { it != null }!!.address
            reputation.get(address).fold(
                onSuccess = {
                    score.value = ScoreContent.Ready(it)
                    paged.reset()
                },
                onFailure = { error ->
                    score.value = if ((error as? AppException)?.error == AppError.ReputationNotFound) {
                        ScoreContent.NoProfile
                    } else {
                        ScoreContent.Error(ErrorCopy.kindOf(error))
                    }
                },
            )
        }
    }

    private fun restoreFilter(): HistoryFilter =
        HistoryFilter.entries.firstOrNull { it.name == savedState.get<String>(KEY_FILTER) } ?: HistoryFilter.All

    private fun historyOf(filter: HistoryFilter, score: ScoreContent, list: PagedState<ReputationEvent>): HistoryContent =
        when {
            score !is ScoreContent.Ready -> HistoryContent.Loading
            list.error != null -> HistoryContent.Error(list.error)
            !list.loaded -> HistoryContent.Loading
            list.items.isEmpty() -> if (filter == HistoryFilter.All) HistoryContent.Empty else HistoryContent.FilteredEmpty
            else -> HistoryContent.Items(list.items, list.hasMore)
        }

    companion object {
        const val PAGE_SIZE = 20
        private const val KEY_FILTER = "reputation_filter"
    }
}
