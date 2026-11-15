package com.latifi.wallet.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Hex and address helpers. Everything is lowercase with a `0x` prefix, as wallets and the API expect. */
object Hex {
    private val ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")
    private val TX_HASH = Regex("^0x[0-9a-fA-F]{64}$")
    private const val DIGITS = "0123456789abcdef"

    /**
     * The `personal_sign` payload of [message]: its UTF-8 bytes as lowercase hex. Line feeds, a
     * trailing newline and non-ASCII characters survive byte for byte, which is what the API
     * checks when it recovers the signer.
     */
    fun utf8ToHex(message: String): String {
        val bytes = message.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(2 + bytes.size * 2).append("0x")
        for (b in bytes) {
            val v = b.toInt() and 0xff
            out.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0f])
        }
        return out.toString()
    }

    /** The inverse of [utf8ToHex]. Throws [IllegalArgumentException] for odd, non-hex or non-UTF-8 input. */
    fun hexToUtf8(hex: String): String {
        val body = if (hex.startsWith("0x") || hex.startsWith("0X")) hex.substring(2) else hex
        require(body.length % 2 == 0) { "Hex text has an odd number of digits" }
        val bytes = ByteArray(body.length / 2)
        for (i in bytes.indices) {
            val high = Character.digit(body[2 * i], 16)
            val low = Character.digit(body[2 * i + 1], 16)
            require(high >= 0 && low >= 0) { "Hex text holds a character that is not a digit" }
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Hex bytes are not valid UTF-8", e)
        }
    }

    /** True for `0x` followed by 40 hex digits, in any letter case. */
    fun isAddress(text: String): Boolean = ADDRESS.matches(text)

    /** The lowercase form of an address. Throws [IllegalArgumentException] when [text] is not an address. */
    fun normalizeAddress(text: String): String {
        require(isAddress(text)) { "Not a wallet address" }
        return text.lowercase()
    }

    /** True for `0x` followed by 64 hex digits, in any letter case. */
    fun isTxHash(text: String): Boolean = TX_HASH.matches(text)

    /** `0x12ab...9f3c`: the first 6 characters, three dots and the last 4. Text that is not an address is returned unchanged. */
    fun shortAddress(address: String): String {
        if (!isAddress(address)) return address
        val lower = address.lowercase()
        return lower.take(6) + "..." + lower.takeLast(4)
    }

    /**
     * `0x12ab...9f3c`: the short form of a transaction reference, the first 6 characters, three dots
     * and the last 4. Text that is not a 64 digit reference is returned unchanged.
     */
    fun shortReference(hash: String): String {
        if (!isTxHash(hash)) return hash
        val lower = hash.lowercase()
        return lower.take(6) + "..." + lower.takeLast(4)
    }
}
