package com.latifi.wallet.data.tx

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.latifi.wallet.domain.tx.RecoveryRecord
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigInteger

class DataStoreRecoveryStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private fun store(file: File): DataStoreRecoveryStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return DataStoreRecoveryStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    /** DataStore frees a file only when its scope has finished, so wait for it before opening a second store. */
    private suspend fun closeFirstStore() {
        scopes.first().coroutineContext[Job]!!.cancelAndJoin()
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private val record = RecoveryRecord(
        requestId = 41,
        kind = TxKind.Repay,
        action = TxAction.Payment,
        loanId = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935"),
        amount = BigInteger.valueOf(125_000_000),
        baseline = BigInteger.valueOf(250_000_000),
        calldataHash = "ab".repeat(32),
        startedAtMillis = 1_762_776_000_000,
    )

    @Test
    fun startsEmpty() = runBlocking {
        assertNull(store(File(tmp.root, "a.preferences_pb")).current())
    }

    @Test
    fun savesTheRecordWithTheRequestIdAndNoHashYet() = runBlocking {
        val s = store(File(tmp.root, "b.preferences_pb"))
        s.save(record)

        val read = s.current()!!

        assertEquals(record, read)
        assertNull(read.txHash)
    }

    @Test
    fun updateAddsTheHashToTheSameRequestOnly() = runBlocking {
        val s = store(File(tmp.root, "c.preferences_pb"))
        s.save(record)

        s.update(requestId = 99, txHash = "0x" + "11".repeat(32))
        assertNull("a hash for another request is not written", s.current()?.txHash)

        s.update(requestId = 41, txHash = "0x" + "22".repeat(32))
        assertEquals("0x" + "22".repeat(32), s.current()?.txHash)
        assertEquals(41L, s.current()?.requestId)
    }

    @Test
    fun aNewRecordReplacesTheOldOneWithoutKeepingItsHash() = runBlocking {
        val s = store(File(tmp.root, "d.preferences_pb"))
        s.save(record.copy(txHash = "0x" + "33".repeat(32)))

        s.save(record.copy(requestId = 42, kind = TxKind.Mint, loanId = null))

        val read = s.current()!!
        assertEquals(42L, read.requestId)
        assertNull(read.txHash)
        assertNull(read.loanId)
    }

    @Test
    fun clearRemovesTheRecord() = runBlocking {
        val s = store(File(tmp.root, "e.preferences_pb"))
        s.save(record)

        s.clear()

        assertNull(s.current())
    }

    @Test
    fun disconnectClearsTheRecordThroughTheSignOutHook() = runBlocking {
        val s = store(File(tmp.root, "f.preferences_pb"))
        s.save(record)

        s.onSignOut()

        assertNull(s.current())
    }

    @Test
    fun theRecordSurvivesANewStoreOnTheSameFile() = runBlocking {
        val file = File(tmp.root, "g.preferences_pb")
        val first = store(file)
        first.save(record)
        first.update(41, "0x" + "44".repeat(32))
        closeFirstStore()

        val second = store(file)

        assertEquals(record.copy(txHash = "0x" + "44".repeat(32)), second.current())
    }
}
