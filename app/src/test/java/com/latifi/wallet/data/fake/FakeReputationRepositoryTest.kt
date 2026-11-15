package com.latifi.wallet.data.fake

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

class FakeReputationRepositoryTest {
    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val repo = FakeReputationRepository(clock)
    private val me = FakeData.DEMO_ADDRESS

    private suspend fun all(type: ReputationEventType? = null): List<ReputationEvent> =
        repo.history(me, type, page = 0, size = 100).getOrThrow().items

    @Test
    fun theDemoWalletHasTheSeededSummary() = runTest {
        val summary = repo.get(me).getOrThrow()

        assertEquals(620, summary.score)
        assertEquals(ReputationLevel.TRUSTED, summary.level)
        assertEquals(Triple(3, 1, 0), Triple(summary.onTimeCount, summary.lateCount, summary.defaultCount))
        assertTrue(summary.profileComplete)
    }

    @Test
    fun theHistoryHasAboutTwelveEventsNewestFirstEndingAtTheScore() = runTest {
        val events = all()

        assertEquals(12, events.size)
        assertEquals(events.sortedByDescending { it.occurredAt }, events)
        assertEquals(620, events.first().scoreAfter)
        // Each row's total is the next older row's total plus its own change.
        events.zipWithNext().forEach { (newer, older) -> assertEquals(older.scoreAfter + newer.delta, newer.scoreAfter) }
        assertEquals(0 + events.last().delta, events.last().scoreAfter)
    }

    @Test
    fun theHistoryHoldsOneProfileEventAndALatePaymentWithDaysLate() = runTest {
        val events = all()

        assertEquals(1, events.count { it.type == ReputationEventType.PROFILE_COMPLETED })
        assertNull(events.single { it.type == ReputationEventType.PROFILE_COMPLETED }.loanId)
        val late = events.single { it.type == ReputationEventType.REPAID_LATE }
        assertEquals(5, late.daysLate)
        assertNotNull(late.loanId)
    }

    @Test
    fun aTypeFilterReturnsExactlyThatType() = runTest {
        ReputationEventType.entries.forEach { type ->
            val events = all(type)
            assertTrue("$type", events.all { it.type == type })
        }
        assertEquals(1, all(ReputationEventType.REPAID_LATE).size)
        assertTrue(all(ReputationEventType.DEFAULTED).isEmpty())
    }

    @Test
    fun theProfileEventOnlyShowsWhenNoTypeIsGiven() = runTest {
        assertTrue(all().any { it.type == ReputationEventType.PROFILE_COMPLETED })
        ReputationEventType.entries.filter { it != ReputationEventType.PROFILE_COMPLETED }.forEach { type ->
            assertFalse(all(type).any { it.type == ReputationEventType.PROFILE_COMPLETED })
        }
    }

    @Test
    fun pagingSplitsTheHistoryWithoutRepeatsAndReportsTheTotal() = runTest {
        val first = repo.history(me, null, page = 0, size = 5).getOrThrow()
        val second = repo.history(me, null, page = 1, size = 5).getOrThrow()
        val third = repo.history(me, null, page = 2, size = 5).getOrThrow()
        val beyond = repo.history(me, null, page = 3, size = 5).getOrThrow()

        assertEquals(listOf(5, 5, 2, 0), listOf(first, second, third, beyond).map { it.items.size })
        assertEquals(12L, first.totalItems)
        assertTrue(first.hasMore)
        assertFalse(third.hasMore)
        assertEquals(12, (first.items + second.items + third.items).map { it.id }.toSet().size)
    }

    @Test
    fun anAddressWithNoProfileIsNotFound() = runTest {
        val unknown = "0x9999999999999999999999999999999999999999"

        assertEquals(AppError.ReputationNotFound, (repo.get(unknown).exceptionOrNull() as AppException).error)
        assertEquals(AppError.ReputationNotFound, (repo.history(unknown, null, 0, 10).exceptionOrNull() as AppException).error)
    }

    @Test
    fun removingTheProfileMakesTheDemoWalletNotFound() = runTest {
        repo.remove(me)

        assertEquals(AppError.ReputationNotFound, (repo.get(me).exceptionOrNull() as AppException).error)
    }

    @Test
    fun everyBorrowerOfTheSeedHasAScoreAtItsLevel() = runTest {
        val expected = mapOf(
            FakeData.LUCIA to ReputationLevel.EXCELLENT,
            FakeData.MATEO to ReputationLevel.TRUSTED,
            FakeData.CAMILA to ReputationLevel.BUILDING,
            FakeData.NEW_BORROWER to ReputationLevel.NEW,
        )
        expected.forEach { (address, level) ->
            val summary = repo.get(address).getOrThrow()
            assertEquals(address, level, summary.level)
            val history = repo.history(address, null, 0, 100).getOrThrow().items
            assertEquals(address, summary.score, history.first().scoreAfter)
        }
    }

    @Test
    fun theScoresInTheSeedMatchTheScoresOnTheLoans() = runTest {
        FakeData.loans(clock).forEach { loan ->
            if (loan.borrower != FakeData.DEMO_ADDRESS) {
                assertEquals("loan ${loan.loanId}", loan.borrowerReputation.level, repo.get(loan.borrower).getOrThrow().level)
            }
        }
    }

    @Test
    fun invalidPagingFailsLikeTheApi() = runTest {
        assertEquals(AppError.Validation("size"), (repo.history(me, null, 0, 0).exceptionOrNull() as AppException).error)
        assertEquals(AppError.Validation("size"), (repo.history(me, null, 0, 101).exceptionOrNull() as AppException).error)
        assertEquals(AppError.Validation("page"), (repo.history(me, null, -1, 10).exceptionOrNull() as AppException).error)
    }

    @Test
    fun aQueuedFailureHitsTheNextCallOnly() = runTest {
        repo.failNext = AppError.Offline

        assertEquals(AppError.Offline, (repo.get(me).exceptionOrNull() as AppException).error)
        assertTrue(repo.get(me).isSuccess)
    }

    @Test
    fun callsAreLoggedWithTheLowercaseAddressAndTheType() = runTest {
        repo.get(me.uppercase().replace("0X", "0x"))
        repo.history(me, ReputationEventType.DEFAULTED, 0, 10)

        assertEquals(listOf(me to null, me to ReputationEventType.DEFAULTED), repo.calls)
    }
}
