package com.latifi.wallet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.math.BigInteger

/**
 * The value of a count-up between [from] and [to] at [fraction] (0 to 1), exact in [BigInteger] so
 * no amount ever passes through a binary float. At 0 it is [from] and at 1 it is [to].
 */
fun interpolateUnits(from: BigInteger, to: BigInteger, fraction: Float): BigInteger {
    if (fraction <= 0f) return from
    if (fraction >= 1f) return to
    val thousandths = BigInteger.valueOf((fraction * 1000f).toLong())
    return from + (to - from).multiply(thousandths).divide(BigInteger.valueOf(1000L))
}

/**
 * Counts from zero up to [target] the first time and from the last shown value afterwards. Under
 * reduced motion it is [target] at once. The final value is always exactly [target].
 */
@Composable
fun rememberCountUp(target: BigInteger): State<BigInteger> {
    val motion = rememberMotion()
    val progress = remember { Animatable(if (motion.enabled) 0f else 1f) }
    var from by remember { mutableStateOf(BigInteger.ZERO) }
    var to by remember { mutableStateOf(if (motion.enabled) target else target) }
    LaunchedEffect(target) {
        if (target != to) {
            from = interpolateUnits(from, to, progress.value)
            to = target
        }
        if (motion.enabled) {
            progress.snapTo(0f)
            progress.animateTo(1f, motion.hero())
        } else {
            from = target
            progress.snapTo(1f)
        }
    }
    return remember { derivedStateOf { interpolateUnits(from, to, progress.value) } }
}
