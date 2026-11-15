package com.latifi.wallet.data.session

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.domain.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * T-05-14-2 and T-05-14-3 on a real Android Keystore (it replaces the retired spike). Proves the
 * envelope that protects the address and the token: a round trip, a key that cannot be exported,
 * a fresh IV on every seal, a tampered blob that fails and signs the person out, and a DataStore
 * file that holds no plaintext. No private key is involved anywhere: the wallet signs.
 */
@RunWith(AndroidJUnit4::class)
class SessionEnvelopeTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val alias = "latifi_session_wrap_test"
    private val envelope = KeystoreEnvelope(alias)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storeFile: File
        get() = File(context.filesDir, "datastore/session_envelope_test.preferences_pb")

    private val session = Session(
        address = "0x52908400098527886e0f7030069857d2e4169ee7",
        token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature-of-the-test",
        expiresAt = Instant.ofEpochMilli(1_762_776_900_123),
    )

    // One DataStore per test: a second one on the same file at the same time is an error. A new
    // KeystoreSessionStore over it has no cache, so it reads and opens the blob again.
    private val data: DataStore<Preferences> by lazy { PreferenceDataStoreFactory.create(scope = scope) { storeFile } }

    @Before
    fun setUp() {
        envelope.reset()
        storeFile.delete()
    }

    @After
    fun tearDown() {
        scope.cancel()
        envelope.reset()
        storeFile.delete()
    }

    private fun loadKey(): SecretKey =
        KeyStore.getInstance(KeystoreEnvelope.ANDROID_KEYSTORE).apply { load(null) }.getKey(alias, null) as SecretKey

    @Test
    fun sealThenOpenReturnsTheSameBytes() {
        val plain = "address and token".toByteArray()

        val sealed = envelope.seal(plain)

        assertEquals(String(plain), String(envelope.open(sealed)))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("address and token"))
    }

    @Test
    fun theKeyCannotBeExportedAndIs256BitsInTheKeystore() {
        envelope.seal(byteArrayOf(1, 2, 3))

        val key = loadKey()

        assertNull("The Keystore key must not be exportable", key.encoded)
        val info = SecretKeyFactory.getInstance(key.algorithm, KeystoreEnvelope.ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(256, info.keySize)
        // Only the number is logged: 0 software, 1 trusted environment, 2 strongbox.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Log.i("SessionEnvelopeTest", "Session key security level ${info.securityLevel}")
    }

    @Test
    fun twoSealsOfTheSameBytesDifferAndStartWithATwelveByteIv() {
        val plain = ByteArray(40) { it.toByte() }

        val first = envelope.seal(plain)
        val second = envelope.seal(plain)

        assertFalse(first.contentEquals(second))
        assertFalse("The IV must change", first.copyOfRange(0, KeystoreEnvelope.IV_BYTES).contentEquals(second.copyOfRange(0, KeystoreEnvelope.IV_BYTES)))
        // IV, ciphertext of the same length as the plain bytes, 16 byte tag.
        assertEquals(KeystoreEnvelope.IV_BYTES + plain.size + 16, first.size)
    }

    @Test
    fun aFlippedCiphertextByteFailsToOpenAndTheKeyIsDeleted() {
        val sealed = envelope.seal("address and token".toByteArray())
        sealed[sealed.size - 20] = (sealed[sealed.size - 20].toInt() xor 0x01).toByte()

        val failure = runCatching { envelope.open(sealed) }.exceptionOrNull()

        assertTrue(failure is GeneralSecurityException)
        assertFalse(KeyStore.getInstance(KeystoreEnvelope.ANDROID_KEYSTORE).apply { load(null) }.containsAlias(alias))
    }

    @Test
    fun aTamperedBlobInTheStoreSignsThePersonOut() = runBlocking {
        val store = KeystoreSessionStore(envelope, data)
        store.save(session)
        val key = stringPreferencesKey("sealed_session")
        val bytes = Base64.getDecoder().decode(data.data.first()[key]!!)
        bytes[bytes.size - 20] = (bytes[bytes.size - 20].toInt() xor 0x01).toByte()
        data.edit { it[key] = Base64.getEncoder().encodeToString(bytes) }

        // A fresh instance has no memory of the good blob.
        val fresh = KeystoreSessionStore(envelope, data)

        assertNull(fresh.current())
        assertNull("The bad blob must be removed", data.data.first()[key])
    }

    @Test
    fun aSavedSessionComesBackAndTheFileHoldsNoPlaintext() = runBlocking {
        val store = KeystoreSessionStore(envelope, data)

        store.save(session)

        assertEquals(session, KeystoreSessionStore(envelope, data).current())
        val files = listOf(context.filesDir, context.dataDir.resolve("datastore"))
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }
            .distinct()
        assertTrue("The scan must cover the session file", files.any { it.name == storeFile.name })
        val secrets = listOf(session.address, session.token, session.expiresAt.toEpochMilli().toString(), session.address.removePrefix("0x"))
        for (file in files) {
            val text = String(file.readBytes(), Charsets.ISO_8859_1)
            for (secret in secrets) {
                assertFalse("Plaintext found in ${file.name}", text.contains(secret, ignoreCase = true))
            }
        }
    }

    @Test
    fun clearRemovesTheBlobAndTheKey() = runBlocking {
        val store = KeystoreSessionStore(envelope, data)
        store.save(session)
        assertNotNull(store.current())

        store.clear()

        assertNull(store.current())
        assertNull(data.data.first()[stringPreferencesKey("sealed_session")])
        assertFalse(KeyStore.getInstance(KeystoreEnvelope.ANDROID_KEYSTORE).apply { load(null) }.containsAlias(alias))
    }
}
