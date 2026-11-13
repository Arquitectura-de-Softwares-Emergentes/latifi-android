package com.latifi.wallet.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every file that starts an animation of its own must read the motion gate, so "remove animations"
 * turns it off. Material components animate themselves and are not scanned.
 */
class MotionGateGuardTest {
    private val root = File("src/main/java/com/latifi/wallet/ui")

    private val animationCalls = Regex(
        "\\b(animate\\w*AsState|Animatable\\(|rememberInfiniteTransition|animateContentSize|AnimatedVisibility\\(|AnimatedContent\\(|animateScrollToPage|updateTransition)",
    )

    /** Files that define the gate itself or hold the tokens. */
    private val exempt = setOf("Motion.kt", "ReducedMotion.kt")

    @Test
    fun everyAnimatingFileUsesTheGate() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in exempt }
            .filter { file ->
                val text = file.readText()
                animationCalls.containsMatchIn(text) &&
                    !text.contains("rememberMotion") && !text.contains("rememberReducedMotion")
            }
            .map { it.relativeTo(root).path }
            .toList()
        assertTrue("Animations without the motion gate: $offenders", offenders.isEmpty())
    }

    @Test
    fun theGateExists() {
        assertTrue(File(root, "components/ReducedMotion.kt").readText().contains("fun rememberMotion"))
    }
}
