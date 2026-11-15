package com.latifi.wallet.ui.common

import com.latifi.wallet.domain.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * What a paged list shows. [error] is set only when there is nothing to show, so it drives the
 * error panel. A failure with data on screen goes to [PagedLoader.failures] instead.
 * [resetToken] grows each time the query changes, so the screen can scroll back to the top.
 */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val totalItems: Long = 0,
    val hasMore: Boolean = false,
    /** True while the first page of a query loads and nothing has been shown for it yet. */
    val isLoadingFirst: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: ErrorKind? = null,
    /** True once a response for the current query has arrived, even an empty one. */
    val loaded: Boolean = false,
    val resetToken: Int = 0,
) {
    val isEmpty: Boolean get() = loaded && items.isEmpty()
}

/**
 * The paging logic every list of the app shares. A view model owns one and gives it a [fetch]
 * lambda that reads the current query; it calls [reset] when the query changes, [refresh] for pull
 * to refresh and the Reload button, and [loadMore] for the next page.
 *
 *  - A failed refresh or next page keeps the items and emits the error on [failures] once, so the
 *    screen shows a snackbar. Only a failure with no data becomes [PagedState.error].
 *  - [reset] drops the items, bumps [PagedState.resetToken] and loads page 0.
 *  - Latest wins: every operation that replaces the list takes a generation number, and an answer
 *    from an older generation is dropped, so a slow response of an old query never overwrites a
 *    newer one. The old request is also cancelled.
 */
class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> Result<Page<T>>,
) {
    private val mutableState = MutableStateFlow(PagedState<T>())
    private val failureChannel = Channel<ErrorKind>(Channel.BUFFERED)
    private var generation = 0
    private var job: Job? = null

    /** The index of the last page that arrived for the current query. */
    private var lastPage = 0

    val state: StateFlow<PagedState<T>> = mutableState.asStateFlow()

    /** Each failure that left the list as it was, once. */
    val failures: Flow<ErrorKind> = failureChannel.receiveAsFlow()

    /** The query changed: clear the list, scroll to the top and load page 0. */
    fun reset() {
        val mine = nextGeneration()
        mutableState.value = PagedState(isLoadingFirst = true, resetToken = mutableState.value.resetToken + 1)
        job = scope.launch { loadFirst(mine) }
    }

    /** Reload page 0 and keep what is on screen until it arrives. */
    fun refresh() {
        val mine = nextGeneration()
        val current = mutableState.value
        val keepScreen = current.loaded || current.items.isNotEmpty()
        mutableState.value = current.copy(
            isRefreshing = keepScreen,
            isLoadingFirst = !keepScreen,
            isLoadingMore = false,
            error = null,
        )
        job = scope.launch { loadFirst(mine) }
    }

    /** Append the next page. Does nothing while another load runs or when there is no next page. */
    fun loadMore() {
        val current = mutableState.value
        if (!current.hasMore || current.isLoadingMore || current.isLoadingFirst || current.isRefreshing) return
        val mine = generation
        mutableState.value = current.copy(isLoadingMore = true)
        job = scope.launch {
            val result = runFetch(lastPage + 1)
            if (mine != generation) return@launch
            result.fold(
                onSuccess = { page ->
                    lastPage = page.page
                    mutableState.value = mutableState.value.copy(
                        items = mutableState.value.items + page.items,
                        totalItems = page.totalItems,
                        hasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(isLoadingMore = false)
                    failureChannel.trySend(ErrorCopy.kindOf(error))
                },
            )
        }
    }

    private suspend fun loadFirst(mine: Int) {
        val result = runFetch(0)
        if (mine != generation) return
        result.fold(
            onSuccess = { page ->
                lastPage = page.page
                mutableState.value = mutableState.value.copy(
                    items = page.items,
                    totalItems = page.totalItems,
                    hasMore = page.hasMore,
                    isLoadingFirst = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = null,
                    loaded = true,
                )
            },
            onFailure = { error ->
                val kind = ErrorCopy.kindOf(error)
                val hasData = mutableState.value.items.isNotEmpty()
                mutableState.value = mutableState.value.copy(
                    isLoadingFirst = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = if (hasData) null else kind,
                )
                if (hasData) failureChannel.trySend(kind)
            },
        )
    }

    private suspend fun runFetch(page: Int): Result<Page<T>> =
        try {
            fetch(page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun nextGeneration(): Int {
        job?.cancel()
        generation += 1
        return generation
    }
}
