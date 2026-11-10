package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HexTest {
    private fun refuses(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun encodesKnownText() {
        assertEquals("0x68656c6c6f", Hex.utf8ToHex("hello"))
        assertEquals("0x", Hex.utf8ToHex(""))
        assertEquals("0x0a", Hex.utf8ToHex("\n"))
    }

    @Test
    fun roundTripKeepsLineFeedsAndTheTrailingNewline() {
        val message = "latifi.example wants you to sign in.\n\nNonce: 8f2c\nIssued At: 2026-11-10T10:00:00Z\n"
        val hex = Hex.utf8ToHex(message)
        assertEquals(message, Hex.hexToUtf8(hex))
        assertTrue(hex.endsWith("0a"))
        assertFalse("no carriage return may appear", hex.contains("0d"))
    }

    @Test
    fun roundTripKeepsNonAsciiCharactersByteForByte() {
        val message = "Billetera: ñandú, comisión de red, 日本語, 😀\n"
        val hex = Hex.utf8ToHex(message)
        assertEquals(message, Hex.hexToUtf8(hex))
        assertEquals(message.toByteArray(Charsets.UTF_8).size * 2 + 2, hex.length)
        assertEquals(hex.lowercase(), hex)
    }

    @Test
    fun decodesWithOrWithoutThePrefixAndInAnyLetterCase() {
        assertEquals("hello", Hex.hexToUtf8("0x68656C6C6F"))
        assertEquals("hello", Hex.hexToUtf8("68656c6c6f"))
    }

    @Test
    fun decodingRefusesBadInput() {
        refuses { Hex.hexToUtf8("0x123") }
        refuses { Hex.hexToUtf8("0xzz") }
        refuses { Hex.hexToUtf8("0xff") }
    }

    @Test
    fun addressValidator() {
        assertTrue(Hex.isAddress("0xccb8ad15a4954138c987a77924ceab468a843266"))
        assertTrue(Hex.isAddress("0xCCB8AD15A4954138C987A77924CEAB468A843266"))
        assertFalse(Hex.isAddress("ccb8ad15a4954138c987a77924ceab468a843266"))
        assertFalse(Hex.isAddress("0xccb8ad15a4954138c987a77924ceab468a84326"))
        assertFalse(Hex.isAddress("0xccb8ad15a4954138c987a77924ceab468a8432666"))
        assertFalse(Hex.isAddress("0xgcb8ad15a4954138c987a77924ceab468a843266"))
        assertFalse(Hex.isAddress(""))
    }

    @Test
    fun normalizeAddressLowercasesOrThrows() {
        assertEquals(
            "0xccb8ad15a4954138c987a77924ceab468a843266",
            Hex.normalizeAddress("0xCCB8AD15A4954138C987A77924CEAB468A843266"),
        )
        refuses { Hex.normalizeAddress("0x1234") }
    }

    @Test
    fun txHashValidator() {
        assertTrue(Hex.isTxHash("0x" + "ab".repeat(32)))
        assertTrue(Hex.isTxHash("0x" + "AB".repeat(32)))
        assertFalse(Hex.isTxHash("0x" + "ab".repeat(31)))
        assertFalse(Hex.isTxHash("0x" + "ab".repeat(33)))
        assertFalse(Hex.isTxHash("ab".repeat(32)))
        assertFalse(Hex.isTxHash("0x" + "zz".repeat(32)))
    }

    @Test
    fun shortAddressKeepsTheFirstSixAndLastFourCharacters() {
        assertEquals("0x12ab...9f3c", Hex.shortAddress("0x12AB00000000000000000000000000000000" + "9F3C"))
        assertEquals("0xccb8...3266", Hex.shortAddress("0xccb8ad15a4954138c987a77924ceab468a843266"))
        assertEquals("not an address", Hex.shortAddress("not an address"))
    }

    @Test
    fun shortReferenceKeepsSixLeadingAndFourTrailingCharacters() {
        val hash = "0x12ab" + "00".repeat(28) + "9F3C"
        assertEquals("0x12ab...9f3c", Hex.shortReference(hash))
    }

    @Test
    fun shortReferenceLeavesTextThatIsNotAReferenceUnchanged() {
        assertEquals("0x1234", Hex.shortReference("0x1234"))
        val address = "0x52908400098527886E0F7030069857D2E4169EE7"
        assertEquals(address, Hex.shortReference(address))
    }
}
