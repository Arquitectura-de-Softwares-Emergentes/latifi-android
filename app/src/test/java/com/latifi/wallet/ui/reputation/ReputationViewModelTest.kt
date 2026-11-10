package com.latifi.wallet.ui.reputation

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeReputationRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.ui.common.ErrorKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.latifi.wallet.support.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReputationViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS
    private val repo = FakeReputationRepository(clock)

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): ReputationViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = ReputationViewModel(handle, repo, sessions)
        runCurrent()
        return vm
    }

    private fun ReputationViewModel.events() = (state.value.history as HistoryContent.Items).events

    private fun TestScope.select(vm: ReputationViewModel, filter: HistoryFilter) {
        vm.onEvent(ReputationEvents.SelectFilter(filter))
        runCurrent()
    }

    @Test
    fun theScoreAndTheCountersLoadForTheSessionAddress() = runTest {
        val vm = viewModel()

        val ready = vm.state.value.score as ScoreContent.Ready
        assertEquals(620, ready.summary.score)
        assertEquals(ReputationLevel.TRUSTED, ready.summary.level)
        assertEquals(Triple(3, 1, 0), Triple(ready.summary.onTimeCount, ready.summary.lateCount, ready.summary.defaultCount))
        assertTrue(repo.calls.all { it.first == address.lowercase() })
    }

    @Test
    fun theHistoryStartsOnAllWithNoTypeAndShowsTheProfileEvent() = runTest {
        val vm = viewModel()

        assertEquals(HistoryFilter.All, vm.state.value.filter)
        assertNull(repo.calls.last().second)
        assertTrue(vm.events().any { it.type == ReputationEventType.PROFILE_COMPLETED })
        assertEquals(12L, vm.state.value.counter)
    }

    @Test
    fun eachChipMapsToTheExactTypeParameter() = runTest {
        val vm = viewModel()
        val expected = mapOf(
            HistoryFilter.All to null,
            HistoryFilter.OnTime to ReputationEventType.REPAID_ON_TIME,
            HistoryFilter.Late to ReputationEventType.REPAID_LATE,
            HistoryFilter.Defaulted to ReputationEventType.DEFAULTED,
        )

        expected.forEach { (filter, type) ->
            // Move away first, so selecting the chip is always a change.
            if (vm.state.value.filter == filter) select(vm, HistoryFilter.Late.takeIf { it != filter } ?: HistoryFilter.OnTime)
            select(vm, filter)
            assertEquals("$filter", type, repo.calls.last().second)
        }
    }

    @Test
    fun theProfileEventOnlyAppearsUnderAll() = runTest {
        val vm = viewModel()

        listOf(HistoryFilter.OnTime, HistoryFilter.Late).forEach { filter ->
            select(vm, filter)
            assertFalse("$filter", (vm.state.value.history as HistoryContent.Items).events.any { it.type == ReputationEventType.PROFILE_COMPLETED })
        }
        select(vm, HistoryFilter.All)
        assertTrue(vm.events().any { it.type == ReputationEventType.PROFILE_COMPLETED })
    }

    @Test
    fun theCounterFollowsTheSelectedFilter() = runTest {
        val vm = viewModel()

        select(vm, HistoryFilter.Late)

        assertEquals(1L, vm.state.value.counter)
        assertEquals(1, vm.events().size)
    }

    @Test
    fun aFilterWithNoEventsIsTheFilteredEmptyStateAndAllIsNotEmpty() = runTest {
        val vm = viewModel()

        select(vm, HistoryFilter.Defaulted)

        assertEquals(HistoryContent.FilteredEmpty, vm.state.value.history)
        assertEquals(0L, vm.state.value.counter)
        vm.onEvent(ReputationEvents.ShowAll)
        runCurrent()
        assertTrue(vm.state.value.history is HistoryContent.Items)
    }

    @Test
    fun aProfileWithNoEventsAtAllIsTheEmptyState() = runTest {
        repo.seed(address, (repo.get(address).getOrThrow()), emptyList())
        val vm = viewModel()

        assertEquals(HistoryContent.Empty, vm.state.value.history)
    }

    @Test
    fun aNotFoundProfileGivesTheAddDetailsStateAndNoHistoryCall() = runTest {
        repo.remove(address)
        val vm = viewModel()

        assertEquals(ScoreContent.NoProfile, vm.state.value.score)
        assertEquals(1, repo.calls.size)
        assertNull(vm.state.value.counter)
    }

    @Test
    fun comingBackAfterSavingDetailsLoadsTheScore() = runTest {
        val summary = repo.get(address).getOrThrow()
        repo.remove(address)
        val vm = viewModel()
        assertEquals(ScoreContent.NoProfile, vm.state.value.score)

        repo.seed(address, summary, emptyList())
        vm.onEvent(ReputationEvents.Resumed)
        runCurrent()

        assertTrue(vm.state.value.score is ScoreContent.Ready)
    }

    @Test
    fun resumingWithAScoreOnScreenReloadsNothing() = runTest {
        val vm = viewModel()
        val calls = repo.calls.size

        vm.onEvent(ReputationEvents.Resumed)
        runCurrent()

        assertEquals(calls, repo.calls.size)
    }

    @Test
    fun aFailedScoreIsAnErrorWithReload() = runTest {
        repo.failNext = AppError.Offline
        val vm = viewModel()

        assertEquals(ScoreContent.Error(ErrorKind.Offline), vm.state.value.score)

        vm.onEvent(ReputationEvents.Reload)
        runCurrent()

        assertTrue(vm.state.value.score is ScoreContent.Ready)
        assertTrue(vm.state.value.history is HistoryContent.Items)
    }

    @Test
    fun aFailedHistoryKeepsTheScoreAndOffersReload() = runTest {
        val vm = viewModel()
        // The score is on screen. Make the next history call fail.
        repo.failNext = AppError.Server
        select(vm, HistoryFilter.OnTime)

        assertTrue(vm.state.value.score is ScoreContent.Ready)
        assertEquals(HistoryContent.Error(ErrorKind.Server), vm.state.value.history)

        vm.onEvent(ReputationEvents.ReloadHistory)
        runCurrent()

        assertTrue(vm.state.value.history is HistoryContent.Items)
    }

    @Test
    fun theFilterSurvivesASavedStateRestore() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        select(first, HistoryFilter.Late)

        val restored = viewModel(handle)

        assertEquals(HistoryFilter.Late, restored.state.value.filter)
        assertEquals(ReputationEventType.REPAID_LATE, repo.calls.last().second)
    }

    @Test
    fun theEventsCounterIsLatestWinsWhenAnEarlierQueryAnswersLate() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = FakeReputationRepository(clock)
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val held = ReputationViewModel(SavedStateHandle(), HeldHistory(slow, gate, holdType = ReputationEventType.REPAID_ON_TIME), sessions)
        runCurrent()

        held.onEvent(ReputationEvents.SelectFilter(HistoryFilter.OnTime))
        runCurrent()
        held.onEvent(ReputationEvents.SelectFilter(HistoryFilter.Late))
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1L, held.state.value.counter)
        assertTrue(held.events().all { it.type == ReputationEventType.REPAID_LATE })
    }
}

/** Holds the history answer for one type until [gate] opens. Everything else passes straight through. */
private class HeldHistory(
    private val inner: FakeReputationRepository,
    private val gate: CompletableDeferred<Unit>,
    private val holdType: ReputationEventType? = ReputationEventType.REPAID_ON_TIME,
) : com.latifi.wallet.domain.reputation.ReputationRepository {
    override suspend fun get(address: String) = inner.get(address)

    override suspend fun history(address: String, type: ReputationEventType?, page: Int, size: Int)
        : Result<com.latifi.wallet.domain.model.Page<com.latifi.wallet.domain.model.ReputationEvent>> {
        if (type == holdType) gate.await()
        return inner.history(address, type, page, size)
    }
}
