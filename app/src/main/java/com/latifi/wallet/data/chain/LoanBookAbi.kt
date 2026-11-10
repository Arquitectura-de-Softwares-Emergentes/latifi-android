package com.latifi.wallet.data.chain

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint8
import java.math.BigInteger

/**
 * Calldata of the LoanBook contract, built with the web3j abi encoder (abi and utils artifacts
 * only). Every function returns `0x` prefixed lowercase hex. The wallet signs the transaction,
 * the app only prepares what it asks the wallet to sign.
 */
object LoanBookAbi {
    /** `requestLoan(uint256,uint16,uint32,uint8)`. Moves no tokens. */
    fun requestLoan(amount: BigInteger, bps: Int, termDays: Int, installments: Int): String {
        require(bps >= 0 && termDays >= 0 && installments >= 0) { "Loan terms are never negative" }
        return FunctionEncoder.encode(
            Function(
                "requestLoan",
                listOf(
                    Uint256(amount),
                    Uint16(BigInteger.valueOf(bps.toLong())),
                    Uint32(BigInteger.valueOf(termDays.toLong())),
                    Uint8(BigInteger.valueOf(installments.toLong())),
                ),
                emptyList(),
            ),
        )
    }
}
