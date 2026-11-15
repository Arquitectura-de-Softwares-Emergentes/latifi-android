package com.latifi.wallet.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Research F5: UI text lives in resources and every clickable names its role. The scan
 * reads Kotlin sources under src/main/java and flags:
 *  - a quoted literal as the first argument of Text(, or in text =, contentDescription =,
 *    onClickLabel = and label = (contentDescription = null is allowed),
 *  - a clickable block with no parameters,
 *  - a clickable( call with no role = in its next 200 characters.
 * Logging calls and files under a debug package are exempt. Material 3 components that
 * carry their own semantics (Button, FilterChip, ListItem, RadioButton, Switch) are not
 * scanned for these patterns: they only matter through the literals they receive.
 */
class NoHardcodedUiTextTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun appSourcesHoldNoHardcodedUiText() {
        assertTrue("source root not found", ResourceScan.SOURCE_ROOT.isDirectory)
        assertTrue("the scan found no Kotlin files", ResourceScan.kotlinFiles(ResourceScan.SOURCE_ROOT).isNotEmpty())

        val findings = scan(ResourceScan.SOURCE_ROOT)

        assertTrue("UI text or clickable problems:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun scannerFlagsLiteralsAndClickablesWithoutRole() {
        val dir = tmp.newFolder("src")
        File(dir, "Bad.kt").writeText(
            """
            package sample
            @Composable fun Bad() {
                Text("Hello")
                Text(
                    text = "World",
                )
                Icon(painter = p, contentDescription = "Star")
                Box(Modifier.semantics { onClick(label = "Open", action = null) })
                Row(Modifier.clickable { go() })
                Row(Modifier.clickable(onClick = { go() }))
                Row(Modifier.combinedClickable(onClickLabel = "Open card") {})
            }
            """.trimIndent(),
        )

        val findings = scan(dir)

        assertTrue(findings.toString(), findings.any { it.contains("Text(") && it.contains(":3") })
        assertTrue(findings.toString(), findings.any { it.contains("text =") && it.contains(":5") })
        assertTrue(findings.toString(), findings.any { it.contains("contentDescription") })
        assertTrue(findings.toString(), findings.any { it.contains("label =") })
        assertTrue(findings.toString(), findings.any { it.contains("clickable block") })
        assertTrue(findings.toString(), findings.any { it.contains("no role") })
        assertTrue(findings.toString(), findings.any { it.contains("onClickLabel") })
        assertEquals(findings.toString(), 7, findings.size)
    }

    @Test
    fun scannerAcceptsResourcesNullDescriptionsRolesLogsAndDebugFiles() {
        val dir = tmp.newFolder("clean")
        File(dir, "Fine.kt").writeText(
            """
            package sample
            // Text("a comment is not scanned")
            /* contentDescription = "neither is a block comment" */
            @Composable fun Fine() {
                Text(stringResource(R.string.app_name))
                Text(text = stringResource(R.string.app_name))
                Icon(painter = p, contentDescription = null)
                Icon(painter = p, contentDescription = stringResource(R.string.app_name))
                Row(Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.app_name), onClick = { go() }))
                Log.d("Tag", buildString { append(text = "inside a log call") })
            }
            """.trimIndent(),
        )
        File(dir, "debug").mkdirs()
        File(dir, "debug/Preview.kt").writeText("""@Composable fun P() { Text("Preview only") }""")

        val findings = scan(dir)

        assertEquals(findings.toString(), emptyList<String>(), findings)
    }

    companion object {
        private val LITERAL_PATTERNS = listOf(
            "Text(" to Regex("""\bText\(\s*"(?:[^"\\]|\\.)*""""),
            "text =" to Regex("""\btext\s*=\s*""""),
            "contentDescription =" to Regex("""\bcontentDescription\s*=\s*""""),
            "onClickLabel =" to Regex("""\bonClickLabel\s*=\s*""""),
            "label =" to Regex("""\blabel\s*=\s*""""),
        )
        private val CLICKABLE_BLOCK = Regex("""\.clickable\s*\{""")
        private val CLICKABLE_CALL = Regex("""\bclickable\s*\(""")
        private val ROLE = Regex("""\brole\s*=""")
        private val LOG_LINE = Regex("""\b(Log|Timber)\.\w+\(|\bprintln\(|\blogger\b""", RegexOption.IGNORE_CASE)

        fun scan(root: File): List<String> {
            val findings = mutableListOf<String>()
            for (file in ResourceScan.kotlinFiles(root)) {
                if (file.invariantSeparatorsPath.contains("/debug/")) continue
                val source = blankOutComments(file.readText())
                fun lineOf(offset: Int) = source.take(offset).count { it == '\n' } + 1
                fun lineText(offset: Int): String {
                    val start = source.lastIndexOf('\n', offset - 1) + 1
                    val end = source.indexOf('\n', offset).let { if (it < 0) source.length else it }
                    return source.substring(start, end)
                }

                for ((label, regex) in LITERAL_PATTERNS) {
                    for (match in regex.findAll(source)) {
                        if (LOG_LINE.containsMatchIn(lineText(match.range.first))) continue
                        findings += "${file.path}:${lineOf(match.range.first)} hard-coded literal in $label"
                    }
                }
                for (match in CLICKABLE_BLOCK.findAll(source)) {
                    findings += "${file.path}:${lineOf(match.range.first)} clickable block has no parameters, so no role"
                }
                for (match in CLICKABLE_CALL.findAll(source)) {
                    val after = source.substring(match.range.last + 1, minOf(source.length, match.range.last + 201))
                    if (!ROLE.containsMatchIn(after)) {
                        findings += "${file.path}:${lineOf(match.range.first)} clickable( call with no role ="
                    }
                }
            }
            return findings
        }

        /** Replaces comment text with spaces, keeping line breaks so line numbers stay true. */
        private fun blankOutComments(text: String): String {
            val blanked = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
                .replace(text) { m -> m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }
            return Regex("""(?m)^[ \t]*//.*$""").replace(blanked) { m -> " ".repeat(m.value.length) }
        }
    }
}
