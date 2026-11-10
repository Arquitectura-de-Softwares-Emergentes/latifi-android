package com.latifi.wallet.ui.overlay

import com.latifi.wallet.ui.nav.Overlays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the one overlay that is open, or none. A repository or a screen asks for a sheet with
 * [show] and the host in the activity draws it above whatever screen is open. Only one value
 * exists at a time, so two sheets can never stack.
 */
@Singleton
class OverlayController @Inject constructor() {
    private val state = MutableStateFlow<Overlays?>(null)

    val current: StateFlow<Overlays?> = state.asStateFlow()

    fun show(overlay: Overlays) {
        state.value = overlay
    }

    fun dismiss() {
        state.value = null
    }
}
