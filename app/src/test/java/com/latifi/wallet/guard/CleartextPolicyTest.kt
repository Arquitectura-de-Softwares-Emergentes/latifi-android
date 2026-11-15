package com.latifi.wallet.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cleartext policy: a release build never allows cleartext traffic. Only the debug source set may
 * allow it, and only for loopback (`localhost` and the emulator host `10.0.2.2`), so a phone can
 * reach the RPC fault proxy through `adb reverse`. This test fails the build when the main
 * manifest or main resources weaken that, or when the debug config grows beyond loopback.
 */
class CleartextPolicyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theRepositoryKeepsCleartextOffInReleaseAndLoopbackOnlyInDebug() {
        // The working directory of a unit test is the app module.
        val module = File(".")
        assertTrue("main manifest not found", File(module, "src/main/AndroidManifest.xml").isFile)

        val findings = check(module)

        assertTrue("Cleartext policy broken:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun theDebugManifestPointsAtTheDebugConfig() {
        val manifest = File("src/debug/AndroidManifest.xml")
        assertTrue("debug manifest not found", manifest.isFile)

        assertTrue(manifest.readText().contains("""android:networkSecurityConfig="@xml/network_security_config""""))
    }

    @Test
    fun aCompliantFixtureHasNoFindings() {
        val module = fixture(
            mainManifest = MAIN_OK,
            mainConfig = null,
            debugConfig = DEBUG_OK,
        )

        assertEquals(emptyList<String>(), check(module))
    }

    @Test
    fun everyWeakeningInASeededBadFixtureIsReported() {
        val module = fixture(
            mainManifest = """<manifest xmlns:android="x"><application android:networkSecurityConfig="@xml/n"/></manifest>""",
            mainConfig = DEBUG_OK,
            debugConfig = """
                <network-security-config>
                  <base-config cleartextTrafficPermitted="true"/>
                  <domain-config cleartextTrafficPermitted="true">
                    <domain includeSubdomains="true">localhost</domain>
                    <domain>example.com</domain>
                  </domain-config>
                </network-security-config>
            """.trimIndent(),
        )

        val findings = check(module)

        assertTrue(findings.toString(), findings.any { it.contains("usesCleartextTraffic") })
        assertTrue(findings.toString(), findings.any { it.contains("networkSecurityConfig") })
        assertTrue(findings.toString(), findings.any { it.contains("main resources") })
        assertTrue(findings.toString(), findings.any { it.contains("base-config") })
        assertTrue(findings.toString(), findings.any { it.contains("subdomains") })
        assertTrue(findings.toString(), findings.any { it.contains("exactly localhost and 10.0.2.2") })
        assertTrue(findings.toString(), findings.any { it.contains("must appear once") })
    }

    @Test
    fun aSecondDomainConfigIsReported() {
        val module = fixture(
            mainManifest = MAIN_OK,
            mainConfig = null,
            debugConfig = """
                <network-security-config>
                  <domain-config cleartextTrafficPermitted="true"><domain>localhost</domain></domain-config>
                  <domain-config cleartextTrafficPermitted="false"><domain>10.0.2.2</domain></domain-config>
                </network-security-config>
            """.trimIndent(),
        )

        assertTrue(check(module).any { it.contains("exactly one domain-config") })
    }

    @Test
    fun aMissingDebugConfigAndAWrongDomainSetAreReported() {
        val missing = fixture(mainManifest = MAIN_OK, mainConfig = null, debugConfig = null)
        assertTrue(check(missing).any { it.contains("debug config is missing") })

        val wrongDomains = fixture(
            mainManifest = MAIN_OK,
            mainConfig = null,
            debugConfig = """
                <network-security-config>
                  <domain-config cleartextTrafficPermitted="true">
                    <domain>localhost</domain>
                    <domain>192.168.1.5</domain>
                  </domain-config>
                </network-security-config>
            """.trimIndent(),
        )
        assertTrue(check(wrongDomains).any { it.contains("exactly localhost and 10.0.2.2") })
    }

    private fun fixture(mainManifest: String, mainConfig: String?, debugConfig: String?): File {
        val root = tmp.newFolder()
        File(root, "src/main").mkdirs()
        File(root, "src/main/AndroidManifest.xml").writeText(mainManifest)
        if (mainConfig != null) {
            File(root, "src/main/res/xml").mkdirs()
            File(root, "src/main/res/xml/network_security_config.xml").writeText(mainConfig)
        }
        if (debugConfig != null) {
            File(root, "src/debug/res/xml").mkdirs()
            File(root, "src/debug/res/xml/network_security_config.xml").writeText(debugConfig)
        }
        return root
    }

    private fun check(module: File): List<String> {
        val findings = mutableListOf<String>()

        val manifest = File(module, "src/main/AndroidManifest.xml").readText()
        if (!Regex("""android:usesCleartextTraffic\s*=\s*"false"""").containsMatchIn(manifest)) {
            findings += "main manifest must set android:usesCleartextTraffic=\"false\""
        }
        if (manifest.contains("networkSecurityConfig")) {
            findings += "main manifest must not set a networkSecurityConfig"
        }

        val mainXml = File(module, "src/main/res/xml")
        if (mainXml.isDirectory && mainXml.listFiles().orEmpty().any { it.name.startsWith("network_security_config") }) {
            findings += "main resources must not hold a network_security_config"
        }

        val debug = File(module, "src/debug/res/xml/network_security_config.xml")
        if (!debug.isFile) {
            findings += "debug config is missing: src/debug/res/xml/network_security_config.xml"
            return findings
        }
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // The config is plain XML. No external entities are ever needed.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(debug)

        if (doc.getElementsByTagName("base-config").length > 0) {
            findings += "debug config must not hold a base-config element"
        }
        val domainConfigs = doc.getElementsByTagName("domain-config")
        if (domainConfigs.length != 1) {
            findings += "debug config must hold exactly one domain-config, found ${domainConfigs.length}"
            return findings
        }
        val config = domainConfigs.item(0) as Element
        if (config.getAttribute("cleartextTrafficPermitted") != "true") {
            findings += "the domain-config must set cleartextTrafficPermitted=\"true\""
        }
        val permits = Regex("""cleartextTrafficPermitted\s*=\s*"true"""").findAll(debug.readText()).count()
        if (permits != 1) {
            findings += "cleartextTrafficPermitted=\"true\" must appear once, inside the domain-config, found $permits"
        }
        val domains = config.getElementsByTagName("domain")
        val names = (0 until domains.length).map { (domains.item(it) as Element).textContent.trim() }
        if (names.toSet() != setOf("localhost", "10.0.2.2") || names.size != 2) {
            findings += "the domain-config must name exactly localhost and 10.0.2.2, found $names"
        }
        for (i in 0 until domains.length) {
            val domain = domains.item(i) as Element
            if (domain.getAttribute("includeSubdomains") == "true") {
                findings += "domain ${domain.textContent.trim()} must not include subdomains"
            }
        }
        return findings
    }

    private companion object {
        const val MAIN_OK =
            """<manifest xmlns:android="x"><application android:usesCleartextTraffic="false"/></manifest>"""
        const val DEBUG_OK = """
            <network-security-config>
              <domain-config cleartextTrafficPermitted="true">
                <domain includeSubdomains="false">localhost</domain>
                <domain includeSubdomains="false">10.0.2.2</domain>
              </domain-config>
            </network-security-config>
        """
    }
}
