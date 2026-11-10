package com.latifi.wallet.data.chain

import com.latifi.wallet.core.Hex
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

/**
 * Calldata of the lending token (an ERC-20 with a public `mint`). Every function returns `0x`
 * prefixed lowercase hex.
 *
 * There is no unlimited approval: [approve] has no default amount, and it refuses any amount
 * above what the contract itself accepts for a principal (T-05-03-1).
 */
object Erc20Abi {
    /** The contract rejects a principal above `uint128 max / 2`, so no approval needs more than 127 bits. */
    private const val MAX_APPROVAL_BITS = 127

    /** `approve(address,uint256)` for exactly [amount]. */
    fun approve(spender: String, amount: BigInteger): String {
        require(amount.signum() >= 0 && amount.bitLength() <= MAX_APPROVAL_BITS) {
            "An approval is for the exact amount of the action, never an unlimited one"
        }
        return FunctionEncoder.encode(
            Function("approve", listOf(Address(Hex.normalizeAddress(spender)), Uint256(amount)), emptyList()),
        )
    }

    /** `approve` for the LoanBook constant. The spender is never an API value. */
    fun approveLoanBook(amount: BigInteger): String = approve(Deployment.LOANBOOK_ADDRESS, amount)

    /** `mint(uint256)`. The token caps it at 1,000 tokens per call and 10,000 per address. */
    fun mint(amount: BigInteger): String {
        require(amount.signum() > 0) { "A mint is above zero" }
        return FunctionEncoder.encode(Function("mint", listOf(Uint256(amount)), emptyList()))
    }

    /** `balanceOf(address)`. */
    fun balanceOf(owner: String): String =
        FunctionEncoder.encode(Function("balanceOf", listOf(Address(Hex.normalizeAddress(owner))), emptyList()))

    /** `allowance(address,address)`. */
    fun allowance(owner: String, spender: String): String =
        FunctionEncoder.encode(
            Function(
                "allowance",
                listOf(Address(Hex.normalizeAddress(owner)), Address(Hex.normalizeAddress(spender))),
                emptyList(),
            ),
        )

    /** The uint256 an `eth_call` returned, or null when the payload is not one word. */
    fun decodeUint256(hex: String): BigInteger? =
        AbiDecoding.decode(hex, Uint256::class.java)?.firstOrNull() as? BigInteger
}
