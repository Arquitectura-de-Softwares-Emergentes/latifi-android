package com.latifi.wallet.ui.nav

import com.latifi.wallet.guard.ResourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The coverage gate for the 20 screens. Routes.kt declares every route and overlay. Every
 * name there must be registered (a `composable(Routes.NAME)` call, or an `Overlays.NAME ->`
 * branch in OverlayHost) or be listed as pending below. The sets below hold what is still
 * unbuilt: each later plan removes the names it registers, and plan 05-12 adds an assertion
 * that both sets are empty. A name cannot sit in both places, and a pending name must exist.
 */
class RoutesRegistryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun everyDeclaredRouteAndOverlayIsRegisteredOrPending() {
        val scan = Scan.of(ResourceScan.SOURCE_ROOT)

        val findings = findings(scan, PENDING_ROUTES, PENDING_OVERLAYS)

        assertTrue("Registry problems:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun theSourceScanSeesTheSameNamesAsTheCompiledRouteMap() {
        val scan = Scan.of(ResourceScan.SOURCE_ROOT)

        assertEquals(Routes.ALL.size, scan.declaredRoutes.size)
        assertEquals(Overlays.entries.map { it.name }.toSet(), scan.declaredOverlays)
    }

    @Test
    fun theShellTheFirstRunGraphTheProfileFormTheLoanDetailsAndTheLenderScreensRegisterTheirRoutes() {
        val scan = Scan.of(ResourceScan.SOURCE_ROOT)

        assertEquals(
            setOf(
                "ONBOARDING", "HOW_IT_WORKS", "CONNECT", "ROLE", "PROFILE_FORM",
                "MAIN", "HOME", "LOANS", "REPUTATION", "INVEST", "PORTFOLIO", "PROFILE", "LOAN_DETAIL",
                "REQUEST_DETAIL", "INVESTMENT_DETAIL",
            ),
            scan.registeredRoutes,
        )
    }

    @Test
    fun scannerReportsUnregisteredNamesAndBadPendingEntries() {
        val dir = tmp.newFolder("fixture")
        File(dir, "Routes.kt").writeText(
            """
            package sample
            object Routes {
                const val A = "a"
                const val B = "b"
                const val C = "c"
            }
            enum class Overlays { X, Y }
            """.trimIndent(),
        )
        File(dir, "Screens.kt").writeText(
            """
            package sample
            // composable(Routes.C) { } is only a comment
            fun NavGraphBuilder.screens() {
                composable(Routes.A) { }
                composable(
                    route = Routes.C,
                ) { }
            }
            """.trimIndent(),
        )
        File(dir, "OverlayHost.kt").writeText(
            """
            package sample
            fun host() { when (current) { Overlays.X -> Sheet() } }
            """.trimIndent(),
        )
        val scan = Scan.of(dir)
        assertEquals(setOf("A", "B", "C"), scan.declaredRoutes)
        assertEquals(setOf("A", "C"), scan.registeredRoutes)
        assertEquals(setOf("X"), scan.registeredOverlays)

        val unregistered = findings(scan, emptySet(), emptySet())
        assertTrue(unregistered.toString(), unregistered.any { it.contains("route B") && it.contains("neither") })
        assertTrue(unregistered.toString(), unregistered.any { it.contains("overlay Y") && it.contains("neither") })
        assertEquals(unregistered.toString(), 2, unregistered.size)

        val bad = findings(scan, setOf("A", "B", "Z"), setOf("X", "Y"))
        assertTrue(bad.toString(), bad.any { it.contains("route A") && it.contains("both") })
        assertTrue(bad.toString(), bad.any { it.contains("route Z") && it.contains("not declared") })
        assertTrue(bad.toString(), bad.any { it.contains("overlay X") && it.contains("both") })
        assertEquals(bad.toString(), 3, bad.size)

        val clean = findings(scan, setOf("B"), setOf("Y"))
        assertEquals(clean.toString(), emptyList<String>(), clean)
    }

    /** What the sources declare and register, as plain name sets. */
    data class Scan(
        val declaredRoutes: Set<String>,
        val declaredOverlays: Set<String>,
        val registeredRoutes: Set<String>,
        val registeredOverlays: Set<String>,
    ) {
        companion object {
            private val CONST = Regex("""\bconst\s+val\s+([A-Z][A-Z0-9_]*)\s*=""")
            private val ROUTE_REGISTRATION =
                Regex("""\bcomposable\(\s*(?:route\s*=\s*)?Routes\.([A-Z][A-Z0-9_]*)""")
            private val OVERLAY_BRANCH = Regex("""\bOverlays\.([A-Z][A-Z0-9_]*)\s*->""")

            fun of(root: File): Scan {
                val routesText = blankOutComments(File(root.walkTopDown().first { it.name == "Routes.kt" }.path).readText())
                val routesBody = routesText.substringAfter("object Routes").substringBefore("enum class Overlays")
                val overlaysBody = routesText.substringAfter("enum class Overlays").substringAfter("{").substringBefore("}")

                val files = ResourceScan.kotlinFiles(root)
                val registeredRoutes = files.flatMap { f ->
                    ROUTE_REGISTRATION.findAll(blankOutComments(f.readText())).map { it.groupValues[1] }.toList()
                }.toSet()
                val registeredOverlays = files.filter { it.name == "OverlayHost.kt" }.flatMap { f ->
                    OVERLAY_BRANCH.findAll(blankOutComments(f.readText())).map { it.groupValues[1] }.toList()
                }.toSet()

                return Scan(
                    declaredRoutes = CONST.findAll(routesBody).map { it.groupValues[1] }.toSet(),
                    declaredOverlays = overlaysBody.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    registeredRoutes = registeredRoutes,
                    registeredOverlays = registeredOverlays,
                )
            }
        }
    }

    companion object {
        /** Routes whose screen is not built yet. Remove a name in the plan that registers it. */
        val PENDING_ROUTES = setOf(
            "REQUEST_LOAN", "PAY", "FUND",
        )

        /** Overlays whose sheet is not built yet. Remove a name in the plan that registers it. */
        val PENDING_OVERLAYS = setOf("COINS")

        fun findings(scan: Scan, pendingRoutes: Set<String>, pendingOverlays: Set<String>): List<String> {
            val found = mutableListOf<String>()
            fun check(kind: String, declared: Set<String>, registered: Set<String>, pending: Set<String>) {
                for (name in declared.sorted()) {
                    val isRegistered = name in registered
                    val isPending = name in pending
                    if (!isRegistered && !isPending) found += "$kind $name is neither registered nor pending"
                    if (isRegistered && isPending) found += "$kind $name is registered and also pending, so it is in both places"
                }
                for (name in pending.sorted()) {
                    if (name !in declared) found += "pending $kind $name is not declared"
                }
            }
            check("route", scan.declaredRoutes, scan.registeredRoutes, pendingRoutes)
            check("overlay", scan.declaredOverlays, scan.registeredOverlays, pendingOverlays)
            return found
        }

        /** Replaces comment text with spaces, keeping line breaks so a commented call is not counted. */
        private fun blankOutComments(text: String): String {
            val blanked = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
                .replace(text) { m -> m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }
            return Regex("""(?m)^[ \t]*//.*$""").replace(blanked) { m -> " ".repeat(m.value.length) }
        }
    }
}
