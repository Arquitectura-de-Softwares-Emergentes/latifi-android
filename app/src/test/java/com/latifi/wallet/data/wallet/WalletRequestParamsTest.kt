package com.latifi.wallet.data.wallet

import com.latifi.wallet.core.Hex
import com.latifi.wallet.domain.wallet.WalletRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pitfall 8 of the research: a changed message fails the sign-in. The params must be exact. */
class WalletRequestParamsTest {
    private val address = "0x52908400098527886e0f7030069857d2e4169ee7"
    private val to = "0x" + "11".repeat(20)

    private fun parse(json: String): JsonArray = Json.parseToJsonElement(json).jsonArray

    @Test
    fun personalSignParamsAreTheHexOfTheMessageThenTheAddress() {
        val params = parse(WalletRequestParams.personalSign("Sign in to LatiFi", address))

        assertEquals(2, params.size)
        assertEquals(Hex.utf8ToHex("Sign in to LatiFi"), params[0].jsonPrimitive.content)
        assertEquals(address, params[1].jsonPrimitive.content)
    }

    @Test
    fun theHexRoundTripEqualsTheMessageWithLfEndingsAndATrailingNewline() {
        val message = "app wants you to sign in\nAddress: $address\nNonce: 8f3a\n\nIssued At: 2026-11-10T12:00:00Z\n"

        val params = parse(WalletRequestParams.personalSign(message, address))

        assertEquals(message, Hex.hexToUtf8(params[0].jsonPrimitive.content))
        assertFalse(params[0].jsonPrimitive.content.contains('\r'))
        assertTrue(Hex.hexToUtf8(params[0].jsonPrimitive.content).endsWith("\n"))
    }

    @Test
    fun aMessageWithQuotesAndNonAsciiTextStaysValidJson() {
        val message = "say \"hola\" \\ señor 100% €"

        val params = parse(WalletRequestParams.personalSign(message, address))

        assertEquals(message, Hex.hexToUtf8(params[0].jsonPrimitive.content))
    }

    @Test
    fun theTransactionParamsAreOneObjectWithFromToDataAndValueAndNoGas() {
        val request = WalletRequest.SendTransaction(from = address, to = to, data = "0xabcdef01", value = "0x0")

        val params = parse(WalletRequestParams.sendTransaction(request))

        assertEquals(1, params.size)
        val tx: JsonObject = params[0].jsonObject
        assertEquals(setOf("from", "to", "data", "value"), tx.keys)
        assertEquals(address, tx.getValue("from").jsonPrimitive.content)
        assertEquals(to, tx.getValue("to").jsonPrimitive.content)
        assertEquals("0xabcdef01", tx.getValue("data").jsonPrimitive.content)
        assertEquals("0x0", tx.getValue("value").jsonPrimitive.content)
        assertFalse(tx.containsKey("gas"))
        assertFalse(tx.containsKey("gasPrice"))
        assertFalse(tx.containsKey("gasLimit"))
    }

    @Test
    fun theChainIdIsPolygonAmoy() {
        assertEquals("eip155:80002", WalletRequestParams.CHAIN_ID)
    }
}
