package com.latifi.wallet.domain.wallet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletWaitTimerTest {
    @Test
    fun theDefaultTimeoutIsFifteenSeconds() {
        assertEquals(15_000L, WalletWaitTimer.DEFAULT_TIMEOUT_MILLIS)
    }

    @Test
    fun isIdleUntilItStarts() = runTest {
        val timer = WalletWaitTimer(backgroundScope)
        assertEquals(WalletWaitTimer.State.Idle, timer.state.value)
    }

    @Test
    fun isNotTimedOutAt14999MillisAndTimedOutAt15000() = runTest {
        val timer = WalletWaitTimer(backgroundScope)
        timer.start()

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(WalletWaitTimer.State.Waiting, timer.state.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(WalletWaitTimer.State.TimedOut, timer.state.value)
    }

    @Test
    fun keepWaitingRestartsTheWaitAndCanRepeatFiveTimes() = runTest {
        val timer = WalletWaitTimer(backgroundScope)
        timer.start()
        repeat(5) {
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals("round $it", WalletWaitTimer.State.TimedOut, timer.state.value)

            timer.keepWaiting()
            assertEquals(WalletWaitTimer.State.Waiting, timer.state.value)
            advanceTimeBy(14_999)
            runCurrent()
            assertEquals("round $it still waiting", WalletWaitTimer.State.Waiting, timer.state.value)
        }
        advanceTimeBy(1)
        runCurrent()
        assertEquals(WalletWaitTimer.State.TimedOut, timer.state.value)
    }

    @Test
    fun stopReturnsToIdleAndNeverTimesOut() = runTest {
        val timer = WalletWaitTimer(backgroundScope)
        timer.start()
        advanceTimeBy(10_000)
        timer.stop()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(WalletWaitTimer.State.Idle, timer.state.value)
    }
}
