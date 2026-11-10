package com.latifi.wallet.data.chain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fails when the copy of deployments/amoy.json and the constants in code drift apart. */
class DeploymentTest {
    private val amoy: JsonObject by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("amoy.json")) { "amoy.json not found" }
        Json.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonObject
    }

    private fun address(contract: String): String =
        amoy.getValue("contracts").jsonObject.getValue(contract).jsonObject.getValue("address").jsonPrimitive.content

    @Test
    fun chainIdEqualsTheConstant() {
        assertEquals(Deployment.CHAIN_ID, amoy.getValue("chainId").jsonPrimitive.content.toLong())
        assertEquals("eip155:${Deployment.CHAIN_ID}", Deployment.CAIP_CHAIN_ID)
    }

    @Test
    fun releaseEqualsTheConstant() {
        assertEquals(Deployment.RELEASE, amoy.getValue("release").jsonPrimitive.content)
    }

    @Test
    fun addressesEqualTheConstants() {
        assertEquals(Deployment.LOANBOOK_ADDRESS, address("LoanBook"))
        assertEquals(Deployment.STABLECOIN_ADDRESS, address("MockStablecoin"))
    }

    @Test
    fun addressesAreLowercaseAndWellFormed() {
        for (value in listOf(Deployment.LOANBOOK_ADDRESS, Deployment.STABLECOIN_ADDRESS)) {
            assertEquals(value.lowercase(), value)
            assertTrue(value, Regex("^0x[0-9a-f]{40}$").matches(value))
        }
    }

    @Test
    fun contractLimitsAreTheDocumentedOnes() {
        assertEquals(5000, Deployment.MAX_RATE_BPS)
        assertEquals(365, Deployment.MAX_TERM_DAYS)
        assertEquals(6, Deployment.MAX_INSTALLMENTS)
        assertEquals(1_000_000_000L, Deployment.MINT_PER_CALL_BASE_UNITS)
        assertEquals(10_000_000_000L, Deployment.MINT_PER_WALLET_BASE_UNITS)
        assertEquals("https://amoy.polygonscan.com", Deployment.EXPLORER_BASE)
    }
}
