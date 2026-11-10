package com.latifi.wallet.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * AUTH-03: no unexplained blockchain jargon where a first-time user reads. Network words
 * are banned in every string. Key and seed words are also banned in the strings of the
 * onboarding, connect, role, sign-in and profile gate screens. The help text that explains
 * a recovery phrase in plain words uses neither list, so it stays allowed.
 */
class NoJargonTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun noJargonReachesAFirstTimeUser() {
        val findings = check(ResourceScan.load(ResourceScan.DEFAULT_VALUES), "en") +
            check(ResourceScan.load(ResourceScan.SPANISH_VALUES), "es")
        assertTrue("Jargon found:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun scannerFlagsJargonAndTheStricterKeyWordsOnFirstTimeKeys() {
        val res = fixture(
            "fixture.xml",
            """
            <resources>
              <string name="help_body">The blockchain pays Gas fees</string>
              <string name="onb2_body">Keep your private key safe</string>
              <string name="connect_body">Tus claves y tu semilla</string>
              <string name="help_recovery">Write down the secret words that restore your wallet</string>
              <string name="signin_body">The seed is hidden, ok</string>
              <string name="profile_gate_title">Hashing is not a word here</string>
              <string name="unrelated">A gasoline station</string>
              <plurals name="role_count">
                <item quantity="one">%d mainnet</item>
                <item quantity="other">%d mainnets</item>
              </plurals>
            </resources>
            """,
        )

        val findings = check(res, "fixture")

        // help_body: blockchain and gas. onb2_body: private key. connect_body: claves and semilla.
        // signin_body: seed. role_count one: mainnet. "mainnets", "Hashing" and "gasoline" are not whole words.
        assertTrue(findings.toString(), findings.any { it.contains("help_body") && it.contains("blockchain") })
        assertTrue(findings.toString(), findings.any { it.contains("help_body") && it.contains("gas", true) })
        assertTrue(findings.toString(), findings.any { it.contains("onb2_body") && it.contains("private key") })
        assertTrue(findings.toString(), findings.any { it.contains("connect_body") && it.contains("claves") })
        assertTrue(findings.toString(), findings.any { it.contains("connect_body") && it.contains("semilla") })
        assertTrue(findings.toString(), findings.any { it.contains("signin_body") && it.contains("seed") })
        assertTrue(findings.toString(), findings.any { it.contains("role_count[one]") && it.contains("mainnet") })
        assertTrue(findings.toString(), findings.none { it.contains("help_recovery") })
        assertTrue(findings.toString(), findings.none { it.contains("unrelated") || it.contains("profile_gate_title") })
        assertEquals(findings.toString(), 7, findings.size)
    }

    private fun fixture(name: String, xml: String): ResourceScan.Resources {
        val file = tmp.newFile(name)
        file.writeText(xml.trimIndent())
        return ResourceScan.parse(file)
    }

    companion object {
        private val NETWORK_WORDS = listOf("blockchain", "cadena de bloques", "gas", "hash", "gwei", "mainnet")
        private val KEY_WORDS =
            listOf("private key", "clave privada", "seed", "semilla", "mnemonic", "keys", "claves")
        private val STRICT_PREFIXES = listOf("onb", "connect_", "role_", "signin_", "profile_gate_")

        private fun wholeWord(word: String) =
            Regex("""(?<![\p{L}\p{N}_])${Regex.escape(word)}(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)

        fun check(res: ResourceScan.Resources, locale: String): List<String> {
            val findings = mutableListOf<String>()
            for ((name, text) in res.allTexts()) {
                val baseName = name.substringBefore('[')
                for (word in NETWORK_WORDS) {
                    if (wholeWord(word).containsMatchIn(text)) findings += "$locale $name uses \"$word\""
                }
                if (STRICT_PREFIXES.any { baseName.startsWith(it) }) {
                    for (word in KEY_WORDS) {
                        if (wholeWord(word).containsMatchIn(text)) findings += "$locale $name uses \"$word\""
                    }
                }
            }
            return findings
        }
    }
}
