package com.latifi.wallet.data.chain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Hash
import java.io.File
import java.math.BigInteger

class Erc20AbiTest {
    private val owner = "0x1111111111111111111111111111111111111111"

    private fun word(value: Long): String = BigInteger.valueOf(value).toString(16).padStart(64, '0')

    private fun addressWord(address: String): String = address.removePrefix("0x").padStart(64, '0')

    private fun selector(signature: String): String = Hash.sha3String(signature).substring(0, 10)

    private fun refuses(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun selectorsMatchTheLiteralsAndTheSignatures() {
        val table = mapOf(
            "approve(address,uint256)" to "0x095ea7b3",
            "mint(uint256)" to "0xa0712d68",
            "balanceOf(address)" to "0x70a08231",
            "allowance(address,address)" to "0xdd62ed3e",
        )
        for ((signature, literal) in table) {
            assertEquals(signature, literal, selector(signature))
        }
    }

    @Test
    fun approveEncodesTheLoanBookSpenderAndTheExactAmount() {
        val expected = "0x095ea7b3" + addressWord(Deployment.LOANBOOK_ADDRESS) + word(50_410_958L)
        assertEquals(expected, Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, BigInteger.valueOf(50_410_958L)))
        assertEquals(expected, Erc20Abi.approveLoanBook(BigInteger.valueOf(50_410_958L)))
    }

    @Test
    fun approveAcceptsAMixedCaseSpenderAndEncodesItLowercase() {
        val mixed = "0xCCB8AD15A4954138C987A77924CEAB468A843266"
        assertEquals(
            Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, BigInteger.TEN),
            Erc20Abi.approve(mixed, BigInteger.TEN),
        )
    }

    @Test
    fun approveRefusesAnUnlimitedAmount() {
        val unlimited = BigInteger.TWO.pow(256).subtract(BigInteger.ONE)
        refuses { Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, unlimited) }
        refuses { Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, BigInteger.TWO.pow(127)) }
        refuses { Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, BigInteger.valueOf(-1)) }
        // The largest principal the contract accepts still fits.
        Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, BigInteger.TWO.pow(127).subtract(BigInteger.ONE))
    }

    @Test
    fun approveRefusesAMalformedSpender() {
        refuses { Erc20Abi.approve("0x1234", BigInteger.ONE) }
        refuses { Erc20Abi.approve("not an address", BigInteger.ONE) }
    }

    @Test
    fun mintEncodesTheAmountAndRefusesZero() {
        assertEquals("0xa0712d68" + word(1_000_000_000L), Erc20Abi.mint(BigInteger.valueOf(1_000_000_000L)))
        refuses { Erc20Abi.mint(BigInteger.ZERO) }
    }

    @Test
    fun balanceOfEncodesTheOwner() {
        assertEquals("0x70a08231" + addressWord(owner), Erc20Abi.balanceOf(owner))
    }

    @Test
    fun allowanceEncodesOwnerAndSpender() {
        assertEquals(
            "0xdd62ed3e" + addressWord(owner) + addressWord(Deployment.LOANBOOK_ADDRESS),
            Erc20Abi.allowance(owner, Deployment.LOANBOOK_ADDRESS),
        )
    }

    @Test
    fun decodeUint256ReadsOneWord() {
        assertEquals(BigInteger.valueOf(1_000_000_000L), Erc20Abi.decodeUint256("0x" + word(1_000_000_000L)))
        assertEquals(BigInteger.ZERO, Erc20Abi.decodeUint256("0x" + word(0)))
    }

    @Test
    fun decodeUint256ReturnsNullForShortData() {
        assertNull(Erc20Abi.decodeUint256("0x"))
        assertNull(Erc20Abi.decodeUint256("0x1234"))
    }

    @Test
    fun noMainSourceHoldsTheUnlimitedApprovalConstant() {
        val root = File("src/main/java")
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)
        val unlimited = "f".repeat(64)
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("the scan found no source files", files.isNotEmpty())
        val offenders = files.filter { it.readText().contains(unlimited, ignoreCase = true) }
        assertFalse("Unlimited approval constant in: $offenders", offenders.isNotEmpty())
    }
}
