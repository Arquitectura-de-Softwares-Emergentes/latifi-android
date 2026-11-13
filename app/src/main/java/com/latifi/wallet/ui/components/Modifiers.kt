package com.latifi.wallet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.latifi.wallet.ui.theme.LatiFiMotion
import kotlinx.coroutines.delay

/** Shrinks a control a little while a finger holds it, with a spring release. No movement under reduced motion. */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = LatiFiMotion.PressedScale,
): Modifier = composed {
    val motion = rememberMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion.enabled) pressedScale else 1f,
        animationSpec = motion.press(),
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Remembers which list items already played their entrance, so scrolling back never replays it. */
@Stable
class EntranceTracker {
    private val seen = HashSet<Any>()

    /** True the first time a [key] asks, false after. */
    fun first(key: Any): Boolean = seen.add(key)
}

@Composable
fun rememberEntranceTracker(): EntranceTracker = remember { EntranceTracker() }

/**
 * A short fade and rise when an item first shows, delayed by its [index] so a column cascades. Only
 * the first [LatiFiMotion.StaggerMaxItems] items wait, the rest come at once. With [tracker] set an
 * item plays once for the life of the screen. Instant under reduced motion.
 */
fun Modifier.staggerIn(index: Int, key: Any? = null, tracker: EntranceTracker? = null): Modifier = composed {
    val motion = rememberMotion()
    val play = remember { motion.enabled && (tracker == null || key == null || tracker.first(key)) }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) {
            delay(index.coerceAtMost(LatiFiMotion.StaggerMaxItems).toLong() * LatiFiMotion.Duration.Stagger)
            progress.animateTo(1f, motion.medium())
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * LatiFiMotion.EntranceOffsetDp * density
    }
}

/** A soft light band that crosses a placeholder. A flat fill under reduced motion. */
fun Modifier.shimmer(base: Color, highlight: Color): Modifier = composed {
    val motion = rememberMotion()
    if (motion.reduced) {
        this
    } else {
        val transition = rememberInfiniteTransition()
        val phase by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        )
        drawWithContent {
            drawContent()
            val width = size.width
            val start = phase * width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + width * 0.6f, size.height * 0.4f),
                ),
            )
        }
    }
}

/** Haptic feedback for key actions. The system setting for touch feedback still applies. */
@Stable
class Haptics internal constructor(private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    /** A light tick for a tap or a selection. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** A firm pulse for an action that was accepted. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A double pulse for something that failed. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

/** `animateItem` that goes through the motion gate: items fade in, fade out and glide to a new place, or just jump. */
@Composable
fun LazyItemScope.motionItem(): Modifier {
    val motion = rememberMotion()
    return Modifier.animateItem(
        fadeInSpec = motion.medium(),
        placementSpec = motion.settle(),
        fadeOutSpec = motion.quick(),
    )
}
