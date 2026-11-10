package com.latifi.wallet.data.chain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.web3j.crypto.Hash
import java.math.BigInteger

class RevertDecoderTest {
    private val someAddress = "0x1111111111111111111111111111111111111111"

    private fun word(value: Long): String = BigInteger.valueOf(value).toString(16).padStart(64, '0')

    private fun addressWord(address: String): String = address.removePrefix("0x").padStart(64, '0')

    /** Each row: the literal selector of the research table, the error signature and the mapped value for payload [args]. */
    private data class Row(val literal: String, val signature: String, val args: String, val expected: ChainFailure)

    private val table = listOf(
        Row("0x29428936", "LoanNotOpen(uint256)", word(5), ChainFailure.NotOpen),
        Row("0x3e257723", "SelfFunding()", "", ChainFailure.SelfFunding),
        Row("0x8db4d94d", "LoanNotActive(uint256)", word(5), ChainFailure.NotActive),
        Row(
            "0x49adcabf", "Overpayment(uint256,uint256)", word(100) + word(50),
            ChainFailure.Overpayment(BigInteger.valueOf(50)),
        ),
        Row(
            "0x5a18bb9d", "NotBorrower(address,address)",
            addressWord(someAddress) + addressWord(Deployment.LOANBOOK_ADDRESS), ChainFailure.NotBorrower,
        ),
        Row(
            "0xfb8f41b2", "ERC20InsufficientAllowance(address,uint256,uint256)",
            addressWord(Deployment.LOANBOOK_ADDRESS) + word(10) + word(50_410_958L),
            ChainFailure.InsufficientAllowance(BigInteger.TEN, BigInteger.valueOf(50_410_958L)),
        ),
        Row(
            "0xe450d38c", "ERC20InsufficientBalance(address,uint256,uint256)",
            addressWord(someAddress) + word(7) + word(100_000_000L),
            ChainFailure.InsufficientBalance(BigInteger.valueOf(7), BigInteger.valueOf(100_000_000L)),
        ),
        Row("0x806b2c5e", "LoanNotFound(uint256)", word(1), ChainFailure.NotFound),
    )

    @Test
    fun theTableHasTheEightErrorsOfTheResearch() {
        assertEquals(8, table.size)
        assertEquals(8, table.map { it.literal }.toSet().size)
    }

    @Test
    fun everyLiteralSelectorEqualsTheOneRecomputedFromTheSignature() {
        for (row in table) {
            assertEquals(row.signature, row.literal, Hash.sha3String(row.signature).substring(0, 10))
        }
    }

    @Test
    fun theConstantsInCodeEqualTheLiterals() {
        assertEquals(
            table.map { it.literal },
            listOf(
                RevertDecoder.LOAN_NOT_OPEN, RevertDecoder.SELF_FUNDING, RevertDecoder.LOAN_NOT_ACTIVE,
                RevertDecoder.OVERPAYMENT, RevertDecoder.NOT_BORROWER, RevertDecoder.INSUFFICIENT_ALLOWANCE,
                RevertDecoder.INSUFFICIENT_BALANCE, RevertDecoder.LOAN_NOT_FOUND,
            ),
        )
    }

    @Test
    fun everyErrorDecodesToItsFailureWithItsArguments() {
        for (row in table) {
            assertEquals(row.signature, row.expected, RevertDecoder.decode(row.literal + row.args))
            assertEquals(row.signature, row.expected, RevertDecoder.decode("0x" + row.literal.removePrefix("0x") + row.args))
        }
    }

    @Test
    fun uppercaseHexAndSurroundingSpacesDecodeToo() {
        val data = "0x806B2C5E" + word(1).uppercase()
        assertEquals(ChainFailure.NotFound, RevertDecoder.decode("  $data  "))
    }

    @Test
    fun emptyOrMissingDataIsUnknownWithoutSelector() {
        assertEquals(ChainFailure.Unknown(null), RevertDecoder.decode(null))
        assertEquals(ChainFailure.Unknown(null), RevertDecoder.decode(""))
        assertEquals(ChainFailure.Unknown(null), RevertDecoder.decode("0x"))
        assertEquals(ChainFailure.Unknown(null), RevertDecoder.decode("0x1234"))
    }

    @Test
    fun anUnknownSelectorIsReportedWithItsFourBytes() {
        assertEquals(ChainFailure.Unknown("0xdeadbeef"), RevertDecoder.decode("0xdeadbeef" + word(1)))
        assertEquals(ChainFailure.Unknown("0x08c379a0"), RevertDecoder.decode("0x08c379a0"))
    }

    @Test
    fun malformedDataNeverThrows() {
        assertEquals(ChainFailure.Unknown(null), RevertDecoder.decode("0xzzzzzzzz"))
        assertEquals(ChainFailure.Unknown(null), RevertDecoder.decode("0x806b2c5"))
        // Known selector, arguments cut short: the failure keeps its selector as Unknown.
        assertEquals(ChainFailure.Unknown("0x49adcabf"), RevertDecoder.decode("0x49adcabf" + word(1)))
        assertEquals(ChainFailure.Unknown("0xfb8f41b2"), RevertDecoder.decode("0xfb8f41b2" + "00"))
    }
}
