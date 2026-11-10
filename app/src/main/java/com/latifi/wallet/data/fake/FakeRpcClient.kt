package com.latifi.wallet.data.fake

import com.latifi.wallet.data.chain.LogEntry
import com.latifi.wallet.domain.chain.CallOutcome
import com.latifi.wallet.domain.chain.Receipt
import com.latifi.wallet.domain.chain.RpcClient
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import java.math.BigInteger

/**
 * What the fake node answers for one transaction hash. The first [nullPolls] calls answer a null
 * receipt, as a node does for a transaction it has not included yet. After that the receipt has
 * [status] and [logs]. [failedPolls] calls fail first, to prove that polling survives errors.
 */
data class FakeReceiptScript(
    val nullPolls: Int = 0,
    val status: String = "0x1",
    val logs: List<LogEntry> = emptyList(),
    val failedPolls: Int = 0,
)

/**
 * A scriptable JSON-RPC node for the demo mode and for tests. A hash with no script gets a
 * successful receipt after [defaultNullPolls] null answers, and the count starts again after it
 * answers, so a second transaction in the demo behaves like the first.
 */
class FakeRpcClient(
    var defaultNullPolls: Int = 2,
    var defaultBalance: BigInteger = BigInteger.TEN.pow(18),
) : RpcClient {
    private val scripts = mutableMapOf<String, FakeReceiptScript>()
    private val polls = mutableMapOf<String, Int>()
    private val balances = mutableMapOf<String, BigInteger>()

    /** Answer of `eth_call`. Return null to let the call succeed with empty data. */
    var onCall: (to: String, data: String, from: String) -> Result<CallOutcome> =
        { _, _, _ -> Result.success(CallOutcome.Success("0x")) }

    /** Every `eth_call` the app made, as (to, data, from). */
    val calls = mutableListOf<Triple<String, String, String>>()

    /** Every receipt request, by hash. */
    val receiptRequests = mutableListOf<String>()

    fun script(hash: String, script: FakeReceiptScript) {
        scripts[hash.lowercase()] = script
        polls.remove(hash.lowercase())
    }

    fun setBalance(address: String, wei: BigInteger) {
        balances[address.lowercase()] = wei
    }

    /** Makes every `eth_call` revert with [data], for example a selector of the revert table. */
    fun revertWith(data: String?) {
        onCall = { _, _, _ -> Result.success(CallOutcome.Reverted(data)) }
    }

    override suspend fun call(to: String, data: String, from: String): Result<CallOutcome> {
        calls += Triple(to, data, from)
        return onCall(to, data, from)
    }

    override suspend fun getReceipt(hash: String): Result<Receipt?> {
        val key = hash.lowercase()
        receiptRequests += key
        val script = scripts[key]
        val count = (polls[key] ?: 0) + 1
        polls[key] = count
        if (script == null) {
            if (count <= defaultNullPolls) return Result.success(null)
            polls.remove(key)
            return Result.success(Receipt("0x1"))
        }
        if (count <= script.failedPolls) return Result.failure(AppException(AppError.Offline))
        if (count <= script.failedPolls + script.nullPolls) return Result.success(null)
        return Result.success(Receipt(script.status, script.logs))
    }

    override suspend fun getBalance(address: String): Result<BigInteger> =
        Result.success(balances[address.lowercase()] ?: defaultBalance)
}
