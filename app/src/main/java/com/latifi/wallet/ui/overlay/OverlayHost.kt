package com.latifi.wallet.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Draws the open overlay above the NavHost. Each overlay gets one branch in the `when`
 * below when its plan builds the sheet, and the same plan removes its name from
 * PENDING_OVERLAYS in RoutesRegistryTest. Until then the overlay draws nothing.
 */
@Composable
fun OverlayHost(controller: OverlayController) {
    val current by controller.current.collectAsStateWithLifecycle()
    when (current) {
        null -> Unit
        else -> Unit
    }
}
