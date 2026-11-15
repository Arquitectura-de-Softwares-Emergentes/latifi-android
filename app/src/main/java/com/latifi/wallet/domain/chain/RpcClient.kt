package com.latifi.wallet.domain.chain

import com.latifi.wallet.data.chain.LogEntry
import java.math.BigInteger

/** What an `eth_call` returned: the result data, or the revert data of a call that would fail. */
sealed interface CallOutcome {
    data class Success(val data: String) : CallOutcome

    /** [data] is the `0x` prefixed revert data, or null when the node gave none. */
    data class Reverted(val data: String?) : CallOutcome
}

/** The part of a transaction receipt the app reads. [status] is `0x1` for success and `0x0` for a revert. */
data class Receipt(val status: String, val logs: List<LogEntry> = emptyList()) {
    val succeeded: Boolean get() = status.equals("0x1", ignoreCase = true)
}

/**
 * The JSON-RPC reads the transaction flow needs. Plan 05-13 adds the OkHttp implementation. A
 * failure to reach the node travels in `Result` as `AppException` with `AppError.Offline`. A
 * transaction the node does not know yet is a null receipt, never a failure.
 */
interface RpcClient {
    /** `eth_call` from [from]. A revert is a [CallOutcome.Reverted], not a failure. */
    suspend fun call(to: String, data: String, from: String): Result<CallOutcome>

    /** `eth_getTransactionReceipt`. Null while the transaction is unknown or pending. */
    suspend fun getReceipt(hash: String): Result<Receipt?>

    /** `eth_getBalance` in wei. It pays the network fee. */
    suspend fun getBalance(address: String): Result<BigInteger>
}
