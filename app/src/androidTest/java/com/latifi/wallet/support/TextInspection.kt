package com.latifi.wallet.support

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse

/** Every text on screen, read from the unmerged tree so merged cards still list each line. */
fun SemanticsNodeInteractionsProvider.allTexts(): List<String> {
    val out = mutableListOf<String>()
    fun walk(node: SemanticsNode) {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
        node.children.forEach(::walk)
    }
    walk(onRoot(useUnmergedTree = true).fetchSemanticsNode())
    return out
}

/** Fails when any text is cut at its end, which is what a fixed height or a line cap would do. */
fun SemanticsNodeInteractionsProvider.assertNoTruncatedText() {
    fun walk(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        if (action != null) {
            val results = mutableListOf<TextLayoutResult>()
            action.invoke(results)
            results.forEach {
                assertFalse(
                    "text is truncated: \"${it.layoutInput.text.text}\" width=${it.didOverflowWidth} " +
                        "height=${it.didOverflowHeight} size=${it.size} lines=${it.lineCount} " +
                        "constraints=${it.layoutInput.constraints}",
                    it.hasVisualOverflow,
                )
            }
        }
        node.children.forEach(::walk)
    }
    walk(onRoot(useUnmergedTree = true).fetchSemanticsNode())
}
