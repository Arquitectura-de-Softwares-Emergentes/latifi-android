package com.latifi.wallet.ui.overlay

import com.latifi.wallet.ui.nav.Overlays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayControllerTest {
    @Test
    fun startsWithNoOverlay() {
        assertNull(OverlayController().current.value)
    }

    @Test
    fun showThenDismissOpensAndClosesTheSheet() {
        val controller = OverlayController()
        controller.show(Overlays.SIGN_IN)
        assertEquals(Overlays.SIGN_IN, controller.current.value)
        controller.dismiss()
        assertNull(controller.current.value)
    }

    @Test
    fun onlyOneOverlayExistsAtATime() {
        val controller = OverlayController()
        controller.show(Overlays.COINS)
        controller.show(Overlays.WALLET_HELP)
        assertEquals(Overlays.WALLET_HELP, controller.current.value)
    }
}
