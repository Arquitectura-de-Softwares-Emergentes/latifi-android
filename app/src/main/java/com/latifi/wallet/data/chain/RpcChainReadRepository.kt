package com.latifi.wallet.data.chain

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.chain.CallOutcome
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.chain.NextInstallment
import com.latifi.wallet.domain.chain.RpcClient
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import kotlinx.coroutines.CancellationException
import java.math.BigInteger
import java.time.Instant

/**
 * The chain reads of the app through a public RPC node: `balanceOf` and `allowance` of the
 * stablecoin, `nextInstallment` of the LoanBook and the native balance. The contract addresses are
 * the constants of [Deployment], never values from an API answer. Calldata and return values use
 * the ABI helpers of plan 05-03, and every number stays a [BigInteger].
 */
class RpcChainReadRepository(private val rpc: RpcClient) : ChainReadRepository {
    override suspend fun tokenBalance(address: String): Result<TokenAmount> =
        readUint(to = Deployment.STABLECOIN_ADDRESS, from = address) { Erc20Abi.balanceOf(address) }
            .map { TokenAmount(it) }

    override suspend fun polBalance(address: String): Result<BigInteger> = rpc.getBalance(address)

    override suspend fun allowance(owner: String, spender: String): Result<BigInteger> =
        readUint(to = Deployment.STABLECOIN_ADDRESS, from = owner) { Erc20Abi.allowance(owner, spender) }

    override suspend fun nextInstallment(loanId: BigInteger): Result<NextInstallment> {
        val outcome = rpc.call(Deployment.LOANBOOK_ADDRESS, LoanBookAbi.nextInstallment(loanId), READ_CALLER)
            .getOrElse { return Result.failure(it) }
        return when (outcome) {
            is CallOutcome.Reverted -> failure(revertError(outcome.data))
            is CallOutcome.Success -> {
                val decoded = LoanBookAbi.decodeNextInstallment(outcome.data) ?: return failure(AppError.Server)
                if (decoded.dueAt.bitLength() >= Long.SIZE_BITS) return failure(AppError.Server)
                Result.success(
                    NextInstallment(
                        index = decoded.index,
                        dueAt = Instant.ofEpochSecond(decoded.dueAt.toLong()),
                        unpaid = TokenAmount(decoded.amountDue),
                    ),
                )
            }
        }
    }

    /** An `eth_call` that returns one `uint256`. A revert or an unreadable answer is a failure. */
    private suspend fun readUint(to: String, from: String, calldata: () -> String): Result<BigInteger> {
        val data = try {
            calldata()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // The address is not a wallet address, so there is nothing to ask the node.
            return failure(AppError.Validation())
        }
        val outcome = rpc.call(to, data, from).getOrElse { return Result.failure(it) }
        return when (outcome) {
            is CallOutcome.Reverted -> failure(AppError.Server)
            is CallOutcome.Success -> Erc20Abi.decodeUint256(outcome.data)?.let { Result.success(it) } ?: failure(AppError.Server)
        }
    }

    /** `LoanNotActive` and `LoanNotFound` mean there is no current installment. Any other revert is a node problem. */
    private fun revertError(data: String?): AppError =
        when (RevertDecoder.decode(data)) {
            ChainFailure.NotActive, ChainFailure.NotFound -> AppError.NotFound
            else -> AppError.Server
        }

    private fun <T> failure(error: AppError): Result<T> = Result.failure(AppException(error))

    private companion object {
        /** A view call needs no caller, so the zero address stands in. */
        const val READ_CALLER = "0x0000000000000000000000000000000000000000"
    }
}
