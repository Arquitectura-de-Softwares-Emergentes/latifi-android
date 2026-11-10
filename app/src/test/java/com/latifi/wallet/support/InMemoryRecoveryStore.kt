package com.latifi.wallet.support

import com.latifi.wallet.domain.tx.RecoveryRecord
import com.latifi.wallet.domain.tx.RecoveryStore

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
}
