package com.latifi.wallet.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.latifi.wallet.domain.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import java.time.Instant

/**
 * T-05-14-2 and T-05-14-3 on the JVM: the session store with a test envelope that XORs the bytes.
 * The real Keystore envelope is proven on an emulator by SessionEnvelopeTest.
 */
class KeystoreSessionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private val session = Session(
        address = "0x52908400098527886e0f7030069857d2e4169ee7",
        token = "header.payload.signature-text",
        expiresAt = Instant.ofEpochMilli(1_762_776_900_000),
    )

    /** Seals by XOR with 0x5a behind a marker byte. Opening anything without the marker fails like a bad tag. */
    private class XorEnvelope : Envelope {
        var resets = 0

        override fun seal(plain: ByteArray): ByteArray = byteArrayOf(MARK) + ByteArray(plain.size) { (plain[it].toInt() xor KEY).toByte() }

        override fun open(sealed: ByteArray): ByteArray {
            if (sealed.isEmpty() || sealed[0] != MARK) throw GeneralSecurityException("bad tag")
            return ByteArray(sealed.size - 1) { (sealed[it + 1].toInt() xor KEY).toByte() }
        }

        override fun reset() {
            resets += 1
        }

        private companion object {
            const val MARK: Byte = 0x01
            const val KEY = 0x5a
        }
    }

    private fun dataStore(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    /** DataStore frees a file only when its scope has finished, so wait for it before opening a second store. */
    private suspend fun closeFirstStore() {
        scopes.first().coroutineContext[Job]!!.cancelAndJoin()
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun startsSignedOut() = runBlocking {
        val store = KeystoreSessionStore(XorEnvelope(), dataStore(File(tmp.root, "a.preferences_pb")))

        assertNull(store.current())
        assertNull(store.session.first())
    }

    @Test
    fun savesAndReadsTheSameSessionAcrossANewStoreInstance() = runBlocking {
        val file = File(tmp.root, "b.preferences_pb")
        KeystoreSessionStore(XorEnvelope(), dataStore(file)).save(session)
        closeFirstStore()

        val reopened = KeystoreSessionStore(XorEnvelope(), dataStore(file))

        assertEquals(session, reopened.current())
        assertEquals(session, reopened.session.first())
    }

    @Test
    fun theFileHoldsNeitherTheAddressNorTheTokenNorTheExpiryAsText() = runBlocking {
        val file = File(tmp.root, "c.preferences_pb")
        KeystoreSessionStore(XorEnvelope(), dataStore(file)).save(session)
        closeFirstStore()

        val text = String(file.readBytes(), Charsets.ISO_8859_1)

        assertFalse(text.contains(session.address))
        assertFalse(text.contains("header.payload"))
        assertFalse(text.contains("1762776900000"))
    }

    @Test
    fun clearEmptiesTheStoreAndForgetsTheKey() = runBlocking {
        val envelope = XorEnvelope()
        val store = KeystoreSessionStore(envelope, dataStore(File(tmp.root, "d.preferences_pb")))
        store.save(session)

        store.clear()

        assertNull(store.current())
        assertNull(store.session.first())
        assertEquals(1, envelope.resets)
    }

    @Test
    fun aBlobThatFailsToOpenReadsAsSignedOutAndIsRemoved() = runBlocking {
        val file = File(tmp.root, "e.preferences_pb")
        val data = dataStore(file)
        val store = KeystoreSessionStore(XorEnvelope(), data)
        store.save(session)
        // A different store instance has no cache, and its envelope rejects what it did not seal.
        val other = KeystoreSessionStore(
            object : Envelope {
                override fun seal(plain: ByteArray) = plain

                override fun open(sealed: ByteArray): ByteArray = throw GeneralSecurityException("tampered")

                override fun reset() = Unit
            },
            data,
        )

        assertNull(other.current())
        assertNull(data.data.first()[stringPreferencesKey("sealed_session")])
        assertNull(store.current())
    }

    @Test
    fun textThatIsNotBase64OrNotASessionIsRemovedToo() = runBlocking {
        val data = dataStore(File(tmp.root, "f.preferences_pb"))
        val store = KeystoreSessionStore(XorEnvelope(), data)
        val key = stringPreferencesKey("sealed_session")

        data.edit { it[key] = "%%% not base64 %%%" }
        assertNull(store.current())
        assertNull(data.data.first()[key])

        // Valid Base64 of sealed bytes whose plain text is not a session.
        val notASession = java.util.Base64.getEncoder().encodeToString(XorEnvelope().seal("{\"x\":1}".toByteArray()))
        data.edit { it[key] = notASession }
        assertNull(store.current())
        assertNull(data.data.first()[key])
    }

    @Test
    fun aFlippedByteIsRejectedByAnEnvelopeThatChecksIntegrity() = runBlocking {
        val data = dataStore(File(tmp.root, "g.preferences_pb"))
        val envelope = XorEnvelope()
        KeystoreSessionStore(envelope, data).save(session)
        val key = stringPreferencesKey("sealed_session")
        val bytes = java.util.Base64.getDecoder().decode(data.data.first()[key]!!)
        bytes[0] = 0x7f
        data.edit { it[key] = java.util.Base64.getEncoder().encodeToString(bytes) }

        val fresh = KeystoreSessionStore(envelope, data)

        assertNull(fresh.current())
        assertTrue(data.data.first()[key] == null)
    }
}
