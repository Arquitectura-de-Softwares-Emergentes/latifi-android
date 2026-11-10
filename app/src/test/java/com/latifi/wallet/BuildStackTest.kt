package com.latifi.wallet

import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.crypto.Hash
import java.math.BigInteger

/** Proves the web3j abi and utils artifacts resolve and encode the LoanBook selectors. */
class BuildStackTest {
    @Test
    fun fundEncodesLoanIdOneWithItsSelector() {
        val data = FunctionEncoder.encode(
            Function("fund", listOf(Uint256(BigInteger.ONE)), emptyList()),
        )
        assertTrue("fund selector, got $data", data.startsWith("0xca1d209d"))
        assertTrue(data.endsWith("0000000000000000000000000000000000000000000000000000000000000001"))
    }

    @Test
    fun requestLoanEncodesItsSelector() {
        val data = FunctionEncoder.encode(
            Function(
                "requestLoan",
                listOf(
                    Uint256(BigInteger.valueOf(100_000_000)),
                    Uint16(BigInteger.valueOf(500)),
                    Uint32(BigInteger.valueOf(90)),
                    Uint8(BigInteger.valueOf(3)),
                ),
                emptyList(),
            ),
        )
        assertTrue("requestLoan selector, got $data", data.startsWith("0x430a09d0"))
    }

    @Test
    fun loanNotOpenErrorSelectorMatches() {
        val selector = Hash.sha3String("LoanNotOpen(uint256)")
        assertTrue("error selector, got $selector", selector.startsWith("0x29428936"))
    }
}
