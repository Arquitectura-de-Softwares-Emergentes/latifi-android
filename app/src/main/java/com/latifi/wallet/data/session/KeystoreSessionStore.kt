package com.latifi.wallet.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.Base64

/** File name of the sealed session store, without the extension DataStore adds. */
const val SESSION_FILE_NAME = "latifi_session"

/**
 * Keeps the address, the token and the expiry as one sealed blob. The blob is the JSON of the
 * session, sealed by an [Envelope], and its Base64 text is the only thing in the DataStore file, so
 * the file holds no readable address, token or expiry. A blob that cannot be opened (changed,
 * sealed with a key that is gone, not Base64, not a session) is deleted and read as signed out.
 *
 * The last opened blob is remembered with its decoded session, so a request that asks for the
 * session many times does not open the envelope each time.
 */
class KeystoreSessionStore(
    private val envelope: Envelope,
    private val store: DataStore<Preferences>,
) : SessionStore {
    @Volatile
    private var cached: Pair<String, Session>? = null

    override val session: Flow<Session?> = store.data.map { prefs -> read(prefs[BLOB]) }.distinctUntilChanged()

    override suspend fun save(session: Session) {
        val blob = Blob(session.address, session.token, session.expiresAt.toEpochMilli())
        val json = Json.encodeToString(Blob.serializer(), blob)
        val text = Base64.getEncoder().encodeToString(envelope.seal(json.toByteArray(Charsets.UTF_8)))
        store.edit { it[BLOB] = text }
        cached = text to session
    }

    override suspend fun current(): Session? = read(store.data.first()[BLOB])

    override suspend fun clear() {
        cached = null
        store.edit { it.remove(BLOB) }
        envelope.reset()
    }

    private suspend fun read(text: String?): Session? {
        if (text == null) return null
        cached?.takeIf { it.first == text }?.let { return it.second }
        val opened = decode(text)
        if (opened == null) {
            // Nothing readable is left to keep. The person signs in again.
            cached = null
            store.edit { it.remove(BLOB) }
        } else {
            cached = text to opened
        }
        return opened
    }

    private fun decode(text: String): Session? =
        try {
            val plain = envelope.open(Base64.getDecoder().decode(text))
            val blob = Json.decodeFromString(Blob.serializer(), String(plain, Charsets.UTF_8))
            Session(blob.address, blob.token, Instant.ofEpochMilli(blob.expiresAt))
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            // Base64 and the JSON decoder both report bad input this way (SerializationException is one).
            null
        }

    @Serializable
    private class Blob(val address: String, val token: String, val expiresAt: Long) {
        override fun toString(): String = "Blob(<hidden>)"
    }

    private companion object {
        val BLOB = stringPreferencesKey("sealed_session")
    }
}
