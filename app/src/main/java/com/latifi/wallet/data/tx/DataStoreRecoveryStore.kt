package com.latifi.wallet.data.tx

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.latifi.wallet.domain.session.SignOutHook
import com.latifi.wallet.domain.tx.RecoveryRecord
import com.latifi.wallet.domain.tx.RecoveryStore
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxKind
import kotlinx.coroutines.flow.first
import java.math.BigInteger

/** File name of the in-flight record store, without the extension DataStore adds. */
const val RECOVERY_FILE_NAME = "latifi_tx_recovery"

/**
 * Keeps the in-flight transaction in a Preferences DataStore. It is built from a given
 * [DataStore] so a test can pass one that lives in a temporary file. Everything in the record is
 * public chain data: ids, amounts, a hash of the calldata and the public transaction hash. It is
 * also a [SignOutHook], so Disconnect clears it.
 */
class DataStoreRecoveryStore(private val store: DataStore<Preferences>) : RecoveryStore, SignOutHook {
    override suspend fun save(record: RecoveryRecord) {
        store.edit { prefs ->
            prefs.clear()
            prefs[REQUEST_ID] = record.requestId
            prefs[KIND] = record.kind.name
            prefs[ACTION] = record.action.name
            record.loanId?.let { prefs[LOAN_ID] = it.toString() }
            record.amount?.let { prefs[AMOUNT] = it.toString() }
            record.baseline?.let { prefs[BASELINE] = it.toString() }
            prefs[CALLDATA_HASH] = record.calldataHash
            prefs[STARTED_AT] = record.startedAtMillis
            record.txHash?.let { prefs[TX_HASH] = it }
        }
    }

    override suspend fun update(requestId: Long, txHash: String) {
        store.edit { prefs ->
            if (prefs[REQUEST_ID] == requestId) prefs[TX_HASH] = txHash
        }
    }

    override suspend fun current(): RecoveryRecord? {
        val prefs = store.data.first()
        val requestId = prefs[REQUEST_ID] ?: return null
        // A record this version cannot read is as good as none: the person never sees a half record.
        val kind = prefs[KIND]?.let { name -> TxKind.entries.firstOrNull { it.name == name } } ?: return null
        val action = prefs[ACTION]?.let { name -> TxAction.entries.firstOrNull { it.name == name } } ?: return null
        val startedAt = prefs[STARTED_AT] ?: return null
        return RecoveryRecord(
            requestId = requestId,
            kind = kind,
            action = action,
            loanId = prefs[LOAN_ID]?.toBigIntegerOrNull(),
            amount = prefs[AMOUNT]?.toBigIntegerOrNull(),
            baseline = prefs[BASELINE]?.toBigIntegerOrNull(),
            calldataHash = prefs[CALLDATA_HASH].orEmpty(),
            startedAtMillis = startedAt,
            txHash = prefs[TX_HASH],
        )
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    override suspend fun clearIf(requestId: Long) {
        store.edit { prefs ->
            if (prefs[REQUEST_ID] == requestId) prefs.clear()
        }
    }

    override suspend fun onSignOut() = clear()

    private fun String.toBigIntegerOrNull(): BigInteger? = try {
        BigInteger(this)
    } catch (_: NumberFormatException) {
        null
    }

    private companion object {
        val REQUEST_ID = longPreferencesKey("request_id")
        val KIND = stringPreferencesKey("kind")
        val ACTION = stringPreferencesKey("action")
        val LOAN_ID = stringPreferencesKey("loan_id")
        val AMOUNT = stringPreferencesKey("amount")
        val BASELINE = stringPreferencesKey("baseline")
        val CALLDATA_HASH = stringPreferencesKey("calldata_hash")
        val STARTED_AT = longPreferencesKey("started_at")
        val TX_HASH = stringPreferencesKey("tx_hash")
    }
}
