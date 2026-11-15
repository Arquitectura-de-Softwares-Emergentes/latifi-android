package com.latifi.wallet.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * String parity between values and values-b+es+419. Every translatable key exists in both,
 * every plural has one and other in both, and the format placeholders of a key match.
 */
class StringsParityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun englishAndSpanishAreInParity() {
        val findings = check(
            ResourceScan.load(ResourceScan.DEFAULT_VALUES),
            ResourceScan.load(ResourceScan.SPANISH_VALUES),
        )
        assertTrue("String parity broken:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun scannerFlagsAMissingKeyAPluralGapAndAPlaceholderMismatch() {
        val en = fixture(
            "en.xml",
            """
            <resources>
              <string name="only_en">Only here</string>
              <string name="fixed" translatable="false">Brand</string>
              <string name="amount">Pay %1${'$'}s of %2${'$'}d</string>
              <string name="fine">Fine</string>
              <plurals name="requests">
                <item quantity="one">%d request</item>
                <item quantity="other">%d requests</item>
              </plurals>
            </resources>
            """,
        )
        val es = fixture(
            "es.xml",
            """
            <resources>
              <string name="only_es">Solo aquí</string>
              <string name="amount">Paga %1${'$'}s</string>
              <string name="fine">Bien</string>
              <plurals name="requests">
                <item quantity="other">%d solicitudes</item>
              </plurals>
            </resources>
            """,
        )

        val findings = check(en, es)

        assertTrue(findings.toString(), findings.any { it.contains("only_en") && it.contains("missing in es") })
        assertTrue(findings.toString(), findings.any { it.contains("only_es") && it.contains("missing in en") })
        assertTrue(findings.toString(), findings.any { it.contains("amount") && it.contains("placeholders") })
        assertTrue(findings.toString(), findings.any { it.contains("requests") && it.contains("one") })
        assertTrue(findings.toString(), findings.none { it.contains("fixed") || it.contains("fine") })
        assertEquals(findings.toString(), 4, findings.size)
    }

    private fun fixture(name: String, xml: String): ResourceScan.Resources {
        val file = tmp.newFile(name)
        file.writeText(xml.trimIndent())
        return ResourceScan.parse(file)
    }

    companion object {
        fun check(en: ResourceScan.Resources, es: ResourceScan.Resources): List<String> {
            val findings = mutableListOf<String>()
            val enKeys = en.strings.filterValues { it.translatable }.keys
            val esKeys = es.strings.filterValues { it.translatable }.keys
            (enKeys - esKeys).forEach { findings += "string $it is missing in es" }
            (esKeys - enKeys).forEach { findings += "string $it is missing in en" }

            for (key in enKeys intersect esKeys) {
                val a = ResourceScan.placeholders(en.strings.getValue(key).value)
                val b = ResourceScan.placeholders(es.strings.getValue(key).value)
                if (a != b) findings += "string $key has different placeholders: en $a, es $b"
            }

            (en.plurals.keys - es.plurals.keys).forEach { findings += "plurals $it is missing in es" }
            (es.plurals.keys - en.plurals.keys).forEach { findings += "plurals $it is missing in en" }
            for ((locale, res) in listOf("en" to en, "es" to es)) {
                for ((name, items) in res.plurals) {
                    for (quantity in listOf("one", "other")) {
                        if (quantity !in items) findings += "plurals $name has no $quantity item in $locale"
                    }
                }
            }
            for (name in en.plurals.keys intersect es.plurals.keys) {
                val a = en.plurals.getValue(name).values.flatMap { ResourceScan.placeholders(it) }.toSet()
                val b = es.plurals.getValue(name).values.flatMap { ResourceScan.placeholders(it) }.toSet()
                if (a != b) findings += "plurals $name has different placeholders: en $a, es $b"
            }
            return findings
        }
    }
}
