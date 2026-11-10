package com.latifi.wallet.data.chain

import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

/** What a failed preflight `eth_call` says went wrong, in the vocabulary the transaction state machine needs. */
sealed interface ChainFailure {
    /** `LoanNotOpen(uint256)`: the request was funded already. */
    data object NotOpen : ChainFailure

    /** `SelfFunding()`: a borrower cannot fund their own request. */
    data object SelfFunding : ChainFailure

    /** `LoanNotActive(uint256)`. */
    data object NotActive : ChainFailure

    /** `Overpayment(uint256,uint256)`: [maxAllowed] is what the current installment still owes. */
    data class Overpayment(val maxAllowed: BigInteger) : ChainFailure

    /** `NotBorrower(address,address)`. */
    data object NotBorrower : ChainFailure

    /** `ERC20InsufficientAllowance(address,uint256,uint256)`. Run the approve step first. */
    data class InsufficientAllowance(val allowance: BigInteger, val needed: BigInteger) : ChainFailure

    /** `ERC20InsufficientBalance(address,uint256,uint256)`. */
    data class InsufficientBalance(val balance: BigInteger, val needed: BigInteger) : ChainFailure

    /** `LoanNotFound(uint256)`. */
    data object NotFound : ChainFailure

    /** Anything else, including empty or short data. [selector] is null when there are no 4 bytes to read. */
    data class Unknown(val selector: String?) : ChainFailure
}

/** Maps the revert data of an `eth_call` to a [ChainFailure]. It never throws. */
object RevertDecoder {
    const val LOAN_NOT_OPEN = "0x29428936"
    const val SELF_FUNDING = "0x3e257723"
    const val LOAN_NOT_ACTIVE = "0x8db4d94d"
    const val OVERPAYMENT = "0x49adcabf"
    const val NOT_BORROWER = "0x5a18bb9d"
    const val INSUFFICIENT_ALLOWANCE = "0xfb8f41b2"
    const val INSUFFICIENT_BALANCE = "0xe450d38c"
    const val LOAN_NOT_FOUND = "0x806b2c5e"

    private val HEX_BODY = Regex("^[0-9a-f]*$")

    fun decode(dataHex: String?): ChainFailure {
        val body = dataHex?.trim()?.lowercase()?.removePrefix("0x") ?: return ChainFailure.Unknown(null)
        if (body.length < 8 || body.length % 2 != 0 || !HEX_BODY.matches(body)) return ChainFailure.Unknown(null)
        val selector = "0x" + body.substring(0, 8)
        val args = "0x" + body.substring(8)
        val failure = when (selector) {
            LOAN_NOT_OPEN -> ChainFailure.NotOpen
            SELF_FUNDING -> ChainFailure.SelfFunding
            LOAN_NOT_ACTIVE -> ChainFailure.NotActive
            NOT_BORROWER -> ChainFailure.NotBorrower
            LOAN_NOT_FOUND -> ChainFailure.NotFound
            OVERPAYMENT -> AbiDecoding.decode(args, Uint256::class.java, Uint256::class.java)
                ?.let { ChainFailure.Overpayment(it[1] as BigInteger) }
            INSUFFICIENT_ALLOWANCE ->
                AbiDecoding.decode(args, Address::class.java, Uint256::class.java, Uint256::class.java)
                    ?.let { ChainFailure.InsufficientAllowance(it[1] as BigInteger, it[2] as BigInteger) }
            INSUFFICIENT_BALANCE ->
                AbiDecoding.decode(args, Address::class.java, Uint256::class.java, Uint256::class.java)
                    ?.let { ChainFailure.InsufficientBalance(it[1] as BigInteger, it[2] as BigInteger) }
            else -> null
        }
        return failure ?: ChainFailure.Unknown(selector)
    }
}
