package com.latifi.wallet.ui.overlay

import com.latifi.wallet.ui.nav.Overlays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the overlays that are open as a stack. A repository or a screen asks for a sheet with
 * [show] and the host in the activity draws the top one above whatever screen is open, so the
 * sign-in sheet can appear above a transaction sheet. [dismiss] closes the top one and
 * `dismiss(overlay)` closes a named one wherever it sits. Asking for the overlay that is already
 * on top changes nothing.
 */
@Singleton
class OverlayController @Inject constructor() {
    private val lock = Any()
    private var stack = emptyList<Overlays>()
    private val top = MutableStateFlow<Overlays?>(null)

    /** The overlay on top of the stack, or null when none is open. */
    val current: StateFlow<Overlays?> = top.asStateFlow()

    fun show(overlay: Overlays) = update { if (it.lastOrNull() == overlay) it else it + overlay }

    /** Closes the top overlay. */
    fun dismiss() = update { it.dropLast(1) }

    /** Closes [overlay] wherever it sits in the stack. */
    fun dismiss(overlay: Overlays) = update { list ->
        val index = list.lastIndexOf(overlay)
        if (index < 0) list else list.filterIndexed { i, _ -> i != index }
    }

    private fun update(change: (List<Overlays>) -> List<Overlays>) = synchronized(lock) {
        stack = change(stack)
        top.value = stack.lastOrNull()
    }
}
