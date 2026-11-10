package com.latifi.wallet.data.chain

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint8
import java.math.BigInteger

/** Current installment of an active loan, as `nextInstallment(uint256)` returns it. */
data class NextInstallment(val index: Int, val dueAt: BigInteger, val amountDue: BigInteger)

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

    /** `fund(uint256)`. The lender must have approved the principal first. */
    fun fund(loanId: BigInteger): String =
        FunctionEncoder.encode(Function("fund", listOf(Uint256(loanId)), emptyList()))

    /** `repay(uint256,uint256)`. The borrower must have approved [amount] first. */
    fun repay(loanId: BigInteger, amount: BigInteger): String {
        require(amount.signum() > 0) { "A payment is above zero" }
        return FunctionEncoder.encode(Function("repay", listOf(Uint256(loanId), Uint256(amount)), emptyList()))
    }

    /** `loanCount()`. */
    fun loanCount(): String = FunctionEncoder.encode(Function("loanCount", emptyList(), emptyList()))

    /** `nextInstallment(uint256)` call data. */
    fun nextInstallment(loanId: BigInteger): String =
        FunctionEncoder.encode(Function("nextInstallment", listOf(Uint256(loanId)), emptyList()))

    /**
     * Decodes the return of `nextInstallment`: three uint256 values named `index`, `dueAt` and
     * `amountDue` in deployments/amoy.json. Returns null when the payload is not three words.
     */
    fun decodeNextInstallment(hex: String): NextInstallment? {
        val values = AbiDecoding.decode(hex, Uint256::class.java, Uint256::class.java, Uint256::class.java)
            ?: return null
        val index = values[0] as? BigInteger ?: return null
        if (index.bitLength() >= Int.SIZE_BITS) return null
        return NextInstallment(
            index = index.toInt(),
            dueAt = values[1] as? BigInteger ?: return null,
            amountDue = values[2] as? BigInteger ?: return null,
        )
    }
}

/** Small wrapper over the web3j decoder that never throws on a short or malformed payload. */
internal object AbiDecoding {
    /** Values of [types] read from [hex], as `BigInteger` for number types and the hex string for an address, or null. */
    fun decode(hex: String, vararg types: Class<out Type<*>>): List<Any>? {
        val body = hex.removePrefix("0x")
        if (body.length < types.size * 64) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val references = types.map { TypeReference.create(it) } as List<TypeReference<Type<*>>>
            val decoded = FunctionReturnDecoder.decode("0x$body", references)
            if (decoded.size != types.size) null else decoded.map { it.value }
        } catch (e: RuntimeException) {
            null
        }
    }
}
