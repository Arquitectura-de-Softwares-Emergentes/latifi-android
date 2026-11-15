package com.latifi.wallet.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.latifi.wallet.ui.theme.LatiFiMotion
import com.latifi.wallet.ui.theme.Motion

/**
 * True when the system animation scale is 0, the "remove animations" setting. Screens then skip
 * pager and progress animation and jump to the new state instead.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        LatiFiMotion.isReduced(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}

/**
 * The motion gate of the design system. Every animation of the app takes its spec or transition
 * from the returned [Motion], so one setting turns all movement off.
 */
@Composable
fun rememberMotion(): Motion {
    val reduced = rememberReducedMotion()
    return remember(reduced) { Motion(reduced) }
}
