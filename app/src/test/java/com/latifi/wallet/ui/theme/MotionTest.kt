package com.latifi.wallet.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The motion gate: reduced motion turns every spec into a snap and every transition into none. */
class MotionTest {
    @Test
    fun animatorScaleZeroCountsAsReducedMotion() {
        assertTrue(LatiFiMotion.isReduced(0f))
        assertFalse(LatiFiMotion.isReduced(1f))
        assertFalse(LatiFiMotion.isReduced(0.5f))
    }

    @Test
    fun reducedMotionSnapsEverySpec() {
        val m = Motion(reduced = true)
        assertFalse(m.enabled)
        listOf(m.quick<Float>(), m.short(), m.medium(), m.long(), m.hero(), m.press(), m.settle()).forEach {
            assertTrue("expected a snap but got $it", it is SnapSpec<*>)
        }
    }

    @Test
    fun reducedMotionShowsNoTransition() {
        val m = Motion(reduced = true)
        assertSame(EnterTransition.None, m.enter())
        assertSame(ExitTransition.None, m.exit())
        assertSame(EnterTransition.None, m.fadeThroughIn())
        assertSame(ExitTransition.None, m.fadeThroughOut())
        assertSame(EnterTransition.None, m.axisEnter(forward = true, offsetPx = 40))
        assertSame(ExitTransition.None, m.axisExit(forward = false, offsetPx = 40))
    }

    @Test
    fun normalMotionUsesTheTokenDurations() {
        val m = Motion(reduced = false)
        assertTrue(m.enabled)
        assertEquals(LatiFiMotion.Duration.Quick, (m.quick<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(LatiFiMotion.Duration.Short, (m.short<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(LatiFiMotion.Duration.Medium, (m.medium<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(LatiFiMotion.Duration.Long, (m.long<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(LatiFiMotion.Duration.Hero, (m.hero<Float>() as TweenSpec<Float>).durationMillis)
        assertTrue(m.press<Float>() is SpringSpec<*>)
        assertTrue(m.settle<Float>() is SpringSpec<*>)
        assertFalse(m.enter() === EnterTransition.None)
    }

    @Test
    fun durationsStayInOrderAndWithinAHalfSecondExceptTheHero() {
        val d = LatiFiMotion.Duration
        assertTrue(d.Quick < d.Short && d.Short < d.Medium && d.Medium < d.Long && d.Long < d.Hero)
        assertTrue(d.Long <= 500)
    }

    @Test
    fun pressedScaleIsSubtle() {
        assertTrue(LatiFiMotion.PressedScale in 0.9f..0.99f)
    }
}
