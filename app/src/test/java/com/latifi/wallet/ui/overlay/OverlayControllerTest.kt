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
    fun overlaysStackAndTheTopOneIsCurrent() {
        val controller = OverlayController()
        controller.show(Overlays.TX_STATUS)
        controller.show(Overlays.SIGN_IN)
        assertEquals(Overlays.SIGN_IN, controller.current.value)

        controller.dismiss()

        assertEquals(Overlays.TX_STATUS, controller.current.value)
    }

    @Test
    fun showingTheOverlayThatIsAlreadyOnTopChangesNothing() {
        val controller = OverlayController()
        controller.show(Overlays.SIGN_IN)
        controller.show(Overlays.SIGN_IN)
        controller.dismiss()
        assertNull(controller.current.value)
    }

    @Test
    fun aNamedOverlayCanBeClosedBelowTheTop() {
        val controller = OverlayController()
        controller.show(Overlays.TX_STATUS)
        controller.show(Overlays.SIGN_IN)

        controller.dismiss(Overlays.TX_STATUS)

        assertEquals(Overlays.SIGN_IN, controller.current.value)
        controller.dismiss()
        assertNull(controller.current.value)
    }

    @Test
    fun dismissingAnOverlayThatIsNotOpenChangesNothing() {
        val controller = OverlayController()
        controller.show(Overlays.COINS)
        controller.dismiss(Overlays.WALLET_HELP)
        assertEquals(Overlays.COINS, controller.current.value)
    }
}
