package com.latifi.wallet.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * AUTH-02: the app never creates, stores or signs with a key. The wallet app owns the keys.
 * This test fails the build when source under src/main/java imports a web3j crypto class
 * other than Hash, or names a private key, a mnemonic or a seed phrase.
 */
class NoKeyMaterialTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun appSourcesHoldNoKeyMaterial() {
        // The working directory of a unit test is the app module.
        val root = File("src/main/java")
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)
        assertTrue("the scan found no source files", sourceFiles(root).isNotEmpty())

        val findings = scan(root)
        assertTrue(
            "Key material found in app sources:\n" + findings.joinToString("\n"),
            findings.isEmpty(),
        )
    }

    @Test
    fun scannerReportsAnOffendingFile() {
        // Seeds a temporary tree so the guard cannot go vacuous.
        val bad = tmp.newFolder("src").resolve("Bad.kt")
        bad.writeText(
            """
            package sample
            import org.web3j.crypto.Keys
            val seedPhrase = "x"
            """.trimIndent(),
        )
        val clean = tmp.root.resolve("src").resolve("Fine.kt")
        clean.writeText(
            """
            package sample
            import org.web3j.crypto.Hash
            val digest = Hash.sha3String("x")
            """.trimIndent(),
        )

        val findings = scan(tmp.root.resolve("src"))

        assertEquals(findings.toString(), 2, findings.size)
        assertTrue(findings.all { it.contains("Bad.kt") })
        assertTrue(findings.any { it.contains("org.web3j.crypto.Keys") })
        assertTrue(findings.any { it.contains("seedPhrase", ignoreCase = true) })
    }

    private fun sourceFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()

    private fun scan(root: File): List<String> {
        val findings = mutableListOf<String>()
        for (file in sourceFiles(root)) {
            file.readLines().forEachIndexed { index, line ->
                val where = "${file.path}:${index + 1}"
                CRYPTO_IMPORT.findAll(line).forEach { match ->
                    val name = match.groupValues[1]
                    if (name != "Hash") findings += "$where imports org.web3j.crypto.$name"
                }
                FORBIDDEN_IDENTIFIERS.find(line)?.let { findings += "$where names ${it.value}" }
                FORBIDDEN_WORDS.find(line)?.let { findings += "$where names ${it.value}" }
            }
        }
        return findings
    }

    private companion object {
        val CRYPTO_IMPORT = Regex("""import\s+org\.web3j\.crypto\.(\w+|\*)""")
        val FORBIDDEN_IDENTIFIERS =
            Regex("""\b(ECKeyPair|Credentials|MnemonicUtils|Bip32ECKeyPair|WalletUtils)\b""")
        val FORBIDDEN_WORDS = Regex("""privateKey|mnemonic|seedPhrase""", RegexOption.IGNORE_CASE)
    }
}
