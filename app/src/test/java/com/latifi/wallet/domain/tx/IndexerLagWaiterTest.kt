package com.latifi.wallet.domain.tx

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndexerLagWaiterTest {
    @Test
    fun anAlreadyTrueCheckReturnsAtOnce() = runTest {
        var asked = 0
        val result = IndexerLagWaiter().await { asked += 1; true }
        assertTrue(result)
        assertEquals(1, asked)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun becomesTrueOnTheSecondTryThreeSecondsLater() = runTest {
        var asked = 0
        val result = async { IndexerLagWaiter().await { asked += 1; asked >= 2 } }

        runCurrent()
        assertEquals(1, asked)
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(1, asked)
        advanceTimeBy(1)
        runCurrent()

        assertTrue(result.await())
        assertEquals(2, asked)
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test
    fun givesUpAfterThirtySecondsOfNeverBeingTrue() = runTest {
        var asked = 0
        val result = async { IndexerLagWaiter().await { asked += 1; false } }

        advanceTimeBy(29_999)
        runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(1)
        runCurrent()

        assertFalse(result.await())
        assertEquals("now, then every 3 seconds up to 30 seconds", 11, asked)
        assertEquals(30_000L, testScheduler.currentTime)
    }
}
