package com.latifi.wallet.support

import com.latifi.wallet.domain.tx.RecoveryRecord
import com.latifi.wallet.domain.tx.RecoveryStore
import kotlinx.coroutines.delay

/** An in-memory [RecoveryStore]. [saves] and [updates] let a test see what the coordinator wrote and in which order. */
class InMemoryRecoveryStore : RecoveryStore {
    var record: RecoveryRecord? = null
        private set
    val saves = mutableListOf<RecoveryRecord>()
    val updates = mutableListOf<Pair<Long, String>>()
    var clears = 0
        private set

    override suspend fun save(record: RecoveryRecord) {
        saves += record
        this.record = record
    }

    override suspend fun update(requestId: Long, txHash: String) {
        updates += requestId to txHash
        record?.takeIf { it.requestId == requestId }?.let { record = it.copy(txHash = txHash) }
    }

    override suspend fun current(): RecoveryRecord? = record

    override suspend fun clear() {
        clears += 1
        record = null
    }

    /** The ids [clearIf] was asked about, in call order. [clears] counts only the unconditional [clear]. */
    val conditionalClears = mutableListOf<Long>()

    /** Makes [clearIf] slow, so a test can see that a late clear leaves the next record alone. */
    var clearDelayMillis = 0L

    override suspend fun clearIf(requestId: Long) {
        conditionalClears += requestId
        if (clearDelayMillis > 0) delay(clearDelayMillis)
        if (record?.requestId == requestId) record = null
    }
}
