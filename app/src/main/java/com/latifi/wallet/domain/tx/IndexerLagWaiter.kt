package com.latifi.wallet.domain.tx

import kotlinx.coroutines.delay

/**
 * The indexer follows the chain a few seconds behind. After a transaction confirms, a screen waits
 * for the API to show the change before it lists the new state: it runs [check] now and then every
 * 3 seconds for up to 30 seconds. It returns whether the check became true in that time. The delay
 * runs on the coroutine clock, so a test controls it with virtual time.
 */
class IndexerLagWaiter(
    private val intervalMillis: Long = INTERVAL_MILLIS,
    private val limitMillis: Long = LIMIT_MILLIS,
) {
    suspend fun await(check: suspend () -> Boolean): Boolean {
        var waited = 0L
        while (true) {
            if (check()) return true
            if (waited >= limitMillis) return false
            delay(intervalMillis)
            waited += intervalMillis
        }
    }

    companion object {
        const val INTERVAL_MILLIS = 3_000L
        const val LIMIT_MILLIS = 30_000L
    }
}
