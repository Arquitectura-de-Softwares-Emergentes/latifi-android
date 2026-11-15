package com.latifi.wallet.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * T-05-13-1: a token, a signature, an `Authorization` value or the RPC URL never reaches a log.
 * The scan reads every source file under `src/main/java`, finds each logging call (`Log.x`,
 * `Timber.x`, `println`, a `logger` or `log` object), joins its lines up to the closing
 * parenthesis, and fails when its arguments name one of the secret identifiers. A second test
 * seeds a temporary tree so the scanner cannot go vacuous.
 */
class NoSecretsInLogsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun noLogCallMentionsASecret() {
        // The working directory of a unit test is the app module.
        val root = File("src/main/java")
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)
        val files = sourceFiles(root)
        assertTrue("the scan found no source files", files.isNotEmpty())

        val findings = scan(root)

        assertTrue("A log call mentions a secret:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun scannerReportsAnOffendingFile() {
        val dir = tmp.newFolder("src")
        dir.resolve("Bad.kt").writeText(
            """
            package sample
            fun a(token: String) { Log.d("TAG", "sent ${'$'}token") }
            fun b(signature: String) {
                Timber.w(
                    "wallet said " +
                        signature,
                )
            }
            fun c(config: Config) { println("rpc " + config.amoyRpcUrl) }
            fun d(h: String) { logger.info("header " + h + " Authorization") }
            """.trimIndent(),
        )
        dir.resolve("Fine.kt").writeText(
            """
            package sample
            fun a() { Log.d("TAG", "loaded 3 loans") }
            fun b(token: String) { send(token) }
            fun c() { println("done") }
            """.trimIndent(),
        )

        val findings = scan(dir)

        assertEquals(findings.toString(), 4, findings.size)
        assertTrue(findings.all { it.contains("Bad.kt") })
    }

    private fun sourceFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()

    private fun scan(root: File): List<String> {
        val findings = mutableListOf<String>()
        for (file in sourceFiles(root)) {
            val lines = file.readLines()
            for (index in lines.indices) {
                val start = LOG_CALL.find(lines[index]) ?: continue
                val call = callText(lines, index, start.range.first)
                SECRET.find(call)?.let { findings += "${file.path}:${index + 1} logs ${it.value}" }
            }
        }
        return findings
    }

    /** The text of the call from its name to the matching closing parenthesis, over up to 12 lines. */
    private fun callText(lines: List<String>, from: Int, column: Int): String {
        val text = StringBuilder()
        var depth = 0
        var opened = false
        for (index in from until minOf(lines.size, from + MAX_LINES)) {
            val line = if (index == from) lines[index].substring(column) else lines[index]
            for (char in line) {
                text.append(char)
                if (char == '(') {
                    depth += 1
                    opened = true
                } else if (char == ')') {
                    depth -= 1
                    if (opened && depth == 0) return text.toString()
                }
            }
            text.append('\n')
        }
        return text.toString()
    }

    private companion object {
        const val MAX_LINES = 12
        val LOG_CALL = Regex("""\b(Log\.[vdiwe]|Timber\.\w+|println|print|[lL]ogger\.\w+|log\.\w+)\s*\(""")
        val SECRET = Regex(
            """\b(token|accessToken|signature|authorization|rpcUrl|amoyRpcUrl|AMOY_RPC_URL|apiBaseUrl)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
