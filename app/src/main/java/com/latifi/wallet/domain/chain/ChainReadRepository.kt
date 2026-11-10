package com.latifi.wallet.domain.chain

import com.latifi.wallet.core.TokenAmount
import java.math.BigInteger
import java.time.Instant

/**
 * The current installment of an active loan as the contract reports it (`nextInstallment`).
 * [index] starts at 1, [dueAt] is when it falls due and [unpaid] is what is still owed on it.
 */
data class NextInstallment(
    val index: Int,
    val dueAt: Instant,
    val unpaid: TokenAmount,
)

/**
 * The reads that must come straight from the chain because the indexer cannot answer them in
 * real time: the token balance, the native balance used for network fees, the allowance a payment
 * or a funding needs, and the current installment of a loan. Failures travel in `Result` as
 * `AppException`. Plan 05-11 adds the JSON-RPC implementation behind this interface.
 *
 * Addresses compare without case. Amounts are base units, never a decimal type.
 */
interface ChainReadRepository {
    /** `balanceOf(address)` on the stablecoin. */
    suspend fun tokenBalance(address: String): Result<TokenAmount>

    /** The native balance in wei (`eth_getBalance`). It pays the network fee, so it never mixes with tUSD. */
    suspend fun polBalance(address: String): Result<BigInteger>

    /** `allowance(owner, spender)` on the stablecoin, in base units. */
    suspend fun allowance(owner: String, spender: String): Result<BigInteger>

    /** `nextInstallment(loanId)`. A loan that is not active fails with `AppError.NotFound`. */
    suspend fun nextInstallment(loanId: BigInteger): Result<NextInstallment>
}
