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
            results.forEach { layout ->
                // hasVisualOverflow also reports a short single line in a wider box, so the check
                // looks at what truncation really is: lines cut by a height limit or an ellipsis,
                // and a line that is wider than the space it was given.
                val maxWidth = layout.layoutInput.constraints.maxWidth
                val cut = layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                    layout.isLineEllipsized(line) || layout.getLineRight(line) > maxWidth + 1f
                }
                assertFalse(
                    "text is truncated: \"${layout.layoutInput.text.text}\" height=${layout.didOverflowHeight} " +
                        "size=${layout.size} lines=${layout.lineCount} constraints=${layout.layoutInput.constraints}",
                    cut,
                )
            }
        }
        node.children.forEach(::walk)
    }
    walk(onRoot(useUnmergedTree = true).fetchSemanticsNode())
}
