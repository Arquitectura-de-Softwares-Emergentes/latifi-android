package com.latifi.wallet.ui.common

import app.cash.turbine.test
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.Page
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagedLoaderTest {
    /** A source of [total] numbers served [size] at a time. A failure queue and a delay steer each call. */
    private class Source(var total: Int, val size: Int = 5) {
        val calls = mutableListOf<Int>()
        var failures = ArrayDeque<AppError>()
        var delayMillis = 0L

        suspend fun page(page: Int): Result<Page<Int>> {
            calls += page
            if (delayMillis > 0) delay(delayMillis)
            failures.removeFirstOrNull()?.let { return Result.failure(AppException(it)) }
            val from = page * size
            val items = (from until minOf(total, from + size)).toList()
            return Result.success(Page(items, page, size, total.toLong()))
        }
    }

    private fun TestScope.loader(source: Source) = PagedLoader(backgroundScope) { source.page(it) }

    @Test
    fun resetLoadsPageZeroAndReportsTotalAndHasMore() = runTest {
        val source = Source(total = 12)
        val loader = loader(source)

        assertTrue(loader.state.value.isLoadingFirst)
        loader.reset()
        assertTrue(loader.state.value.isLoadingFirst)
        runCurrent()

        val state = loader.state.value
        assertEquals(listOf(0, 1, 2, 3, 4), state.items)
        assertEquals(12L, state.totalItems)
        assertTrue(state.hasMore)
        assertFalse(state.isLoadingFirst)
        assertTrue(state.loaded)
        assertNull(state.error)
        assertEquals(listOf(0), source.calls)
    }

    @Test
    fun loadMoreAppendsTheNextPagesAndStopsWhenThereAreNoMore() = runTest {
        val source = Source(total = 12)
        val loader = loader(source)
        loader.reset()
        runCurrent()

        loader.loadMore()
        runCurrent()
        assertEquals((0..9).toList(), loader.state.value.items)
        assertTrue(loader.state.value.hasMore)

        loader.loadMore()
        runCurrent()
        assertEquals((0..11).toList(), loader.state.value.items)
        assertFalse(loader.state.value.hasMore)

        loader.loadMore()
        runCurrent()
        assertEquals(listOf(0, 1, 2), source.calls)
    }

    @Test
    fun loadMoreWaitsForALoadThatIsRunning() = runTest {
        val source = Source(total = 12).apply { delayMillis = 100 }
        val loader = loader(source)
        loader.reset()
        loader.loadMore()
        runCurrent()
        assertEquals(listOf(0), source.calls)

        advanceTimeBy(101)
        runCurrent()
        loader.loadMore()
        loader.loadMore()
        runCurrent()
        assertEquals(listOf(0, 1), source.calls)
    }

    @Test
    fun aFailedRefreshKeepsTheItemsAndEmitsTheFailureOnce() = runTest {
        val source = Source(total = 12)
        val loader = loader(source)
        loader.reset()
        runCurrent()

        loader.failures.test {
            source.failures += AppError.Offline
            loader.refresh()
            assertTrue(loader.state.value.isRefreshing)
            assertEquals(5, loader.state.value.items.size)
            runCurrent()

            assertEquals(ErrorKind.Offline, awaitItem())
            val state = loader.state.value
            assertEquals(listOf(0, 1, 2, 3, 4), state.items)
            assertNull(state.error)
            assertFalse(state.isRefreshing)
            expectNoEvents()
        }
    }

    @Test
    fun aRefreshThatWorksReplacesTheItemsWithPageZero() = runTest {
        val source = Source(total = 12)
        val loader = loader(source)
        loader.reset()
        runCurrent()
        loader.loadMore()
        runCurrent()

        source.total = 3
        loader.refresh()
        runCurrent()

        assertEquals(listOf(0, 1, 2), loader.state.value.items)
        assertEquals(3L, loader.state.value.totalItems)
        assertFalse(loader.state.value.hasMore)
    }

    @Test
    fun aFailedNextPageKeepsTheItemsEmitsTheFailureAndCanBeRetried() = runTest {
        val source = Source(total = 12)
        val loader = loader(source)
        loader.reset()
        runCurrent()

        loader.failures.test {
            source.failures += AppError.Server
            loader.loadMore()
            runCurrent()
            assertEquals(ErrorKind.Server, awaitItem())
            assertEquals(5, loader.state.value.items.size)
            assertTrue(loader.state.value.hasMore)
            assertFalse(loader.state.value.isLoadingMore)

            loader.loadMore()
            runCurrent()
            assertEquals(10, loader.state.value.items.size)
            expectNoEvents()
        }
    }

    @Test
    fun aFirstLoadFailureBecomesTheErrorStateAndNotASnackbar() = runTest {
        val source = Source(total = 12).apply { failures += AppError.Offline }
        val loader = loader(source)

        loader.failures.test {
            loader.reset()
            runCurrent()

            val state = loader.state.value
            assertEquals(ErrorKind.Offline, state.error)
            assertTrue(state.items.isEmpty())
            assertFalse(state.isLoadingFirst)
            assertFalse(state.loaded)
            expectNoEvents()
        }
    }

    @Test
    fun reloadingFromTheErrorStateShowsTheSkeletonThenTheData() = runTest {
        val source = Source(total = 4).apply { failures += AppError.Server }
        val loader = loader(source)
        loader.reset()
        runCurrent()
        assertEquals(ErrorKind.Server, loader.state.value.error)

        loader.refresh()
        assertTrue(loader.state.value.isLoadingFirst)
        assertNull(loader.state.value.error)
        runCurrent()

        assertEquals(listOf(0, 1, 2, 3), loader.state.value.items)
        assertNull(loader.state.value.error)
    }

    @Test
    fun anEmptyAnswerIsLoadedAndEmpty() = runTest {
        val loader = loader(Source(total = 0))
        loader.reset()
        runCurrent()

        assertTrue(loader.state.value.isEmpty)
        assertFalse(loader.state.value.hasMore)

        loader.refresh()
        assertTrue(loader.state.value.isRefreshing)
        assertFalse(loader.state.value.isLoadingFirst)
    }

    @Test
    fun aQueryChangeClearsTheItemsAndBumpsTheResetToken() = runTest {
        val source = Source(total = 12)
        val loader = loader(source)
        loader.reset()
        runCurrent()
        val before = loader.state.value.resetToken

        source.total = 2
        loader.reset()

        assertTrue(loader.state.value.items.isEmpty())
        assertTrue(loader.state.value.isLoadingFirst)
        assertEquals(before + 1, loader.state.value.resetToken)
        runCurrent()
        assertEquals(listOf(0, 1), loader.state.value.items)
    }

    @Test
    fun aSlowAnswerOfAnOldQueryNeverOverwritesANewerOne() = runTest {
        var label = "old"
        val loader = PagedLoader<String>(backgroundScope) { page ->
            val mine = label
            // The old request ignores cancellation, as a call that already left the device would.
            withContext(NonCancellable) { delay(if (mine == "old") 1_000 else 100) }
            Result.success(Page(listOf(mine), page, 5, if (mine == "old") 99L else 1L))
        }

        loader.reset()
        runCurrent()
        label = "new"
        loader.reset()
        advanceTimeBy(101)
        runCurrent()
        assertEquals(listOf("new"), loader.state.value.items)
        assertEquals(1L, loader.state.value.totalItems)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("new"), loader.state.value.items)
        assertEquals(1L, loader.state.value.totalItems)
    }

    @Test
    fun aNewQueryCancelsTheOldRequest() = runTest {
        val source = Source(total = 12).apply { delayMillis = 1_000 }
        val loader = loader(source)
        loader.reset()
        runCurrent()
        source.delayMillis = 10
        loader.reset()
        advanceTimeBy(11)
        runCurrent()

        assertEquals(listOf(0, 0), source.calls)
        assertEquals(5, loader.state.value.items.size)
    }

    @Test
    fun aThrownExceptionBecomesAFailureNotACrash() = runTest {
        val loader = PagedLoader<Int>(backgroundScope) { error("boom") }
        loader.reset()
        runCurrent()

        assertEquals(ErrorKind.Server, loader.state.value.error)
    }
}
