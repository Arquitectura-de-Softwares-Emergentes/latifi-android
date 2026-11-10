package com.latifi.wallet.domain.tx

import java.math.BigInteger

/**
 * The one transaction that is in flight, as the app keeps it on disk so a process kill does not
 * lose it. It is public chain data only: ids, amounts, a hash of the calldata and the public
 * transaction hash. No token, signature or key is ever part of it.
 *
 * [requestId] is written first, when the wallet has been asked and the transaction hash does not
 * exist yet. [txHash] is added when the wallet answers. [amount] and [baseline] are what a
 * reconciliation compares against after a restart: the amount the step moves and the balance,
 * allowance or repaid amount seen before it ran.
 */
data class RecoveryRecord(
    val requestId: Long,
    val kind: TxKind,
    val action: TxAction,
    val loanId: BigInteger?,
    val amount: BigInteger?,
    val baseline: BigInteger?,
    val calldataHash: String,
    val startedAtMillis: Long,
    val txHash: String? = null,
)

/** Holds at most one [RecoveryRecord]. A new transaction replaces the previous record. */
interface RecoveryStore {
    suspend fun save(record: RecoveryRecord)

    /** Adds the transaction hash to the record of [requestId]. A record of another request is left alone. */
    suspend fun update(requestId: Long, txHash: String)

    suspend fun current(): RecoveryRecord?

    suspend fun clear()
}
