package com.latifi.wallet.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * D-06: an action label is a verb plus the object it acts on. No string may be a bare
 * Cancel, Continue, Save, Done, OK or Submit (or its Spanish twin), and the keys that held
 * those labels stay removed.
 */
class NoGenericLabelsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun noGenericActionLabelExists() {
        val findings = check(
            ResourceScan.load(ResourceScan.DEFAULT_VALUES),
            ResourceScan.load(ResourceScan.SPANISH_VALUES),
        )
        assertTrue("Generic labels found:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun scannerFlagsGenericValuesAndRemovedKeys() {
        val en = fixture(
            "en.xml",
            """
            <resources>
              <string name="a">  cancel </string>
              <string name="b">OK</string>
              <string name="c">Cancel payment</string>
              <string name="action_save">Store it</string>
            </resources>
            """,
        )
        val es = fixture(
            "es.xml",
            """
            <resources>
              <string name="a">Listo</string>
              <string name="b">Pagar cuota</string>
              <plurals name="action_close">
                <item quantity="one">Cerrar</item>
                <item quantity="other">Cerrar</item>
              </plurals>
            </resources>
            """,
        )

        val findings = check(en, es)

        assertTrue(findings.toString(), findings.any { it.contains("a") && it.contains("cancel") })
        assertTrue(findings.toString(), findings.any { it.contains("OK") })
        assertTrue(findings.toString(), findings.any { it.contains("Listo") })
        assertTrue(findings.toString(), findings.any { it.contains("action_save") })
        assertTrue(findings.toString(), findings.any { it.contains("action_close") })
        assertTrue(findings.toString(), findings.none { it.contains("Cancel payment") || it.contains("Pagar cuota") })
        assertEquals(findings.toString(), 5, findings.size)
    }

    private fun fixture(name: String, xml: String): ResourceScan.Resources {
        val file = tmp.newFile(name)
        file.writeText(xml.trimIndent())
        return ResourceScan.parse(file)
    }

    companion object {
        private val ENGLISH = setOf("cancel", "continue", "save", "done", "ok", "submit")
        private val SPANISH = setOf("cancelar", "continuar", "guardar", "listo", "aceptar", "enviar")
        private val REMOVED_KEYS = listOf(
            "action_continue", "action_cancel", "action_done", "action_save",
            "action_retry", "action_close", "action_copy",
        )

        fun check(en: ResourceScan.Resources, es: ResourceScan.Resources): List<String> {
            val findings = mutableListOf<String>()
            for ((locale, res, banned) in listOf(
                Triple("en", en, ENGLISH),
                Triple("es", es, SPANISH),
            )) {
                for ((name, text) in res.allTexts()) {
                    if (text.trim().lowercase() in banned) {
                        findings += "$locale $name is a generic label: \"$text\""
                    }
                }
                for (key in REMOVED_KEYS) {
                    if (key in res.allNames()) findings += "$locale still has the removed key $key"
                }
            }
            return findings
        }
    }
}
