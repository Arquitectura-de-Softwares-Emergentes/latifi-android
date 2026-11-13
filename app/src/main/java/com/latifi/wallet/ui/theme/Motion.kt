package com.latifi.wallet.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Immutable

/**
 * Motion tokens of the LatiFi design system: every duration, easing, spring and scale in one place.
 * Nothing in the UI animates with a raw number. Components get their specs from [Motion], which is
 * the only gate that knows about the "remove animations" setting.
 */
object LatiFiMotion {
    object Duration {
        const val Quick = 120
        const val Short = 200
        const val Medium = 320
        const val Long = 480

        /** Count-up of a balance and the sweep of the reputation ring. */
        const val Hero = 900

        /** Delay between two items of a staggered entrance. */
        const val Stagger = 45
    }

    object Easing {
        /** Default for elements that move inside the screen. */
        val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Elements that arrive: quick start, soft landing. */
        val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        /** Elements that leave. */
        val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    }

    /** Scale of a pressed control. */
    const val PressedScale = 0.97f

    /** Only the first items of a list stagger in, the rest appear at once. */
    const val StaggerMaxItems = 6

    /** Entrance travel in dp, small on purpose. */
    const val EntranceOffsetDp = 14

    /** Horizontal travel of the shared axis between two screens, in dp. */
    const val SharedAxisOffsetDp = 28

    /** The screen number at which the system "remove animations" setting counts as on. */
    fun isReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f
}

/**
 * The one gate between an animation and the screen. When [reduced] is true (the system animator
 * scale is 0, the "remove animations" setting) every spec is a snap and every transition is
 * instant, so nothing moves. Get one with `rememberMotion()`.
 */
@Immutable
class Motion(val reduced: Boolean) {
    val enabled: Boolean get() = !reduced

    fun <T> quick(): FiniteAnimationSpec<T> = timed(LatiFiMotion.Duration.Quick)
    fun <T> short(): FiniteAnimationSpec<T> = timed(LatiFiMotion.Duration.Short)
    fun <T> medium(): FiniteAnimationSpec<T> = timed(LatiFiMotion.Duration.Medium)
    fun <T> long(): FiniteAnimationSpec<T> = timed(LatiFiMotion.Duration.Long)

    /** Long ease-out for numbers that count up and rings that fill. */
    fun <T> hero(): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(LatiFiMotion.Duration.Hero, easing = LatiFiMotion.Easing.EmphasizedDecelerate)

    /** Springy press and release of a control, no overshoot trouble at 0.97. */
    fun <T> press(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** A soft settle for things that land: a check badge, a selected indicator. */
    fun <T> settle(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    private fun <T> timed(millis: Int): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(millis, easing = LatiFiMotion.Easing.Standard)

    /** Fade and a short rise, for a card or a row that appears. */
    fun enter(): EnterTransition =
        if (reduced) EnterTransition.None else fadeIn(medium()) + scaleIn(medium(), initialScale = 0.96f)

    fun exit(): ExitTransition =
        if (reduced) ExitTransition.None else fadeOut(quick()) + scaleOut(quick(), targetScale = 0.98f)

    /** Fade through between two top level destinations. */
    fun fadeThroughIn(): EnterTransition = if (reduced) {
        EnterTransition.None
    } else {
        fadeIn(tween(LatiFiMotion.Duration.Medium, delayMillis = LatiFiMotion.Duration.Quick, easing = LatiFiMotion.Easing.EmphasizedDecelerate)) +
            scaleIn(tween(LatiFiMotion.Duration.Medium, delayMillis = LatiFiMotion.Duration.Quick, easing = LatiFiMotion.Easing.EmphasizedDecelerate), initialScale = 0.94f)
    }

    fun fadeThroughOut(): ExitTransition =
        if (reduced) ExitTransition.None else fadeOut(tween(LatiFiMotion.Duration.Quick, easing = LatiFiMotion.Easing.EmphasizedAccelerate))

    /** Shared axis X, forward: the new screen comes from the right. [offsetPx] is the travel in px. */
    fun axisEnter(forward: Boolean, offsetPx: Int): EnterTransition = if (reduced) {
        EnterTransition.None
    } else {
        slideInHorizontally(tween(LatiFiMotion.Duration.Long, easing = LatiFiMotion.Easing.EmphasizedDecelerate)) {
            if (forward) offsetPx else -offsetPx
        } + fadeIn(tween(LatiFiMotion.Duration.Medium, delayMillis = LatiFiMotion.Duration.Quick, easing = LatiFiMotion.Easing.EmphasizedDecelerate))
    }

    fun axisExit(forward: Boolean, offsetPx: Int): ExitTransition = if (reduced) {
        ExitTransition.None
    } else {
        slideOutHorizontally(tween(LatiFiMotion.Duration.Long, easing = LatiFiMotion.Easing.EmphasizedAccelerate)) {
            if (forward) -offsetPx else offsetPx
        } + fadeOut(tween(LatiFiMotion.Duration.Quick, easing = LatiFiMotion.Easing.EmphasizedAccelerate))
    }
}
