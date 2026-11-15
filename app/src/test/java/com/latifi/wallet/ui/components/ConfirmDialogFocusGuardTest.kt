package com.latifi.wallet.ui.components

import com.latifi.wallet.guard.ResourceScan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A JVM guard for a bug an emulator found: a dialog composes its content in its own window,
 * so a focus request made from the caller of AlertDialog runs before the safe button exists
 * and is lost. The request must sit inside the dialog content, after `dismissButton =`.
 */
class ConfirmDialogFocusGuardTest {
    private val source = File(ResourceScan.SOURCE_ROOT, "com/latifi/wallet/ui/components/ConfirmDialog.kt").readText()

    @Test
    fun theFocusRequestRunsInsideTheDialogContent() {
        val dialogStart = source.indexOf("AlertDialog(")
        val dismissStart = source.indexOf("dismissButton =")
        val helperCall = source.indexOf("FocusSafeActionOnShow(safeFocus)")
        assertTrue("AlertDialog call not found", dialogStart >= 0)
        assertTrue("dismissButton not found", dismissStart > dialogStart)
        assertTrue(
            "the focus helper must be called from the dismissButton content, not before the dialog",
            helperCall > dismissStart,
        )
    }

    @Test
    fun noFocusRequestRunsInTheCallerOfTheDialog() {
        val beforeDialog = source.substringBefore("AlertDialog(")
        val functionBody = beforeDialog.substringAfter("fun ConfirmDialog(")
        assertTrue(
            "requestFocus on the safe action must not be called before AlertDialog",
            !Regex("""safeFocus\.requestFocus\(""").containsMatchIn(functionBody),
        )
    }

    @Test
    fun theHelperRetriesAtMostAFewFramesAndThenStops() {
        assertTrue(source.contains("withFrameNanos"))
        assertTrue(source.contains("private const val MaxAttempts = 5"))
    }
}
