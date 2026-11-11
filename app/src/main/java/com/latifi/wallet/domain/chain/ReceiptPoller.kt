package com.latifi.wallet.domain.chain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * Asks for the receipt of a transaction every 2 seconds. A null receipt means the network has not
 * included it yet, so it keeps asking. A failed call (no connection, a rate limit, a timeout)
 * is retried silently at the same pace: the person is told nothing, because a lost call says
 * nothing about the transaction. The delay runs on the coroutine clock, so a test with virtual
 * time controls it.
 */
class ReceiptPoller(
    private val rpc: RpcClient,
    private val intervalMillis: Long = INTERVAL_MILLIS,
) {
    /**
     * Emits the first non-null receipt and ends. It never ends on its own: the caller bounds it
     * (the coordinator wraps it in a time window) and may ask for a slower [every] pace.
     */
    fun poll(hash: String, every: Long = intervalMillis): Flow<Receipt> = flow {
        while (true) {
            delay(every)
            val receipt = try {
                rpc.getReceipt(hash).getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                null
            }
            if (receipt != null) {
                emit(receipt)
                return@flow
            }
        }
    }

    companion object {
        const val INTERVAL_MILLIS = 2_000L
    }
}
