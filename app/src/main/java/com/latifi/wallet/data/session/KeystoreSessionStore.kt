package com.latifi.wallet.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.session.SessionStore
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
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

    // A file that cannot be read (IOException) is read as signed out. It never crashes the collector,
    // so a damaged file cannot make the app fail again at every start.
    override val session: Flow<Session?> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> read(prefs[BLOB]) }
        .distinctUntilChanged()

    override suspend fun save(session: Session) {
        val blob = Blob(session.address, session.token, session.expiresAt.toEpochMilli())
        val json = Json.encodeToString(Blob.serializer(), blob)
        val text = Base64.getEncoder().encodeToString(envelope.seal(json.toByteArray(Charsets.UTF_8)))
        store.edit { it[BLOB] = text }
        cached = text to session
    }

    override suspend fun current(): Session? {
        val text = try {
            store.data.first()[BLOB]
        } catch (_: IOException) {
            return null
        }
        return read(text)
    }

    override suspend fun clear() {
        cached = null
        try {
            store.edit { it.remove(BLOB) }
        } catch (_: IOException) {
            // The key is forgotten below, so whatever stays in a file nobody can write is unreadable.
        } finally {
            envelope.reset()
        }
    }

    private suspend fun read(text: String?): Session? {
        if (text == null) return null
        cached?.takeIf { it.first == text }?.let { return it.second }
        return when (val decoded = decode(text)) {
            is Decoded.Opened -> {
                cached = text to decoded.session
                decoded.session
            }
            Decoded.Unreadable -> {
                // Nothing readable is left to keep. The person signs in again.
                cached = null
                try {
                    store.edit { it.remove(BLOB) }
                } catch (_: IOException) {
                    // The next start tries again.
                }
                null
            }
            // The Keystore failed for now. The blob may be fine, so it stays and the person is read as signed out.
            Decoded.Unavailable -> null
        }
    }

    private sealed interface Decoded {
        data class Opened(val session: Session) : Decoded

        data object Unreadable : Decoded

        data object Unavailable : Decoded
    }

    private fun decode(text: String): Decoded =
        try {
            val plain = envelope.open(Base64.getDecoder().decode(text))
            val blob = Json.decodeFromString(Blob.serializer(), String(plain, Charsets.UTF_8))
            Decoded.Opened(Session(blob.address, blob.token, Instant.ofEpochMilli(blob.expiresAt)))
        } catch (e: GeneralSecurityException) {
            Decoded.Unreadable
        } catch (e: IllegalArgumentException) {
            // Base64 and the JSON decoder both report bad input this way (SerializationException is one).
            Decoded.Unreadable
        } catch (e: ProviderException) {
            // A Keystore that is unstable on this device (key generation or cipher init failed).
            Decoded.Unavailable
        } catch (e: IOException) {
            Decoded.Unavailable
        }

    @Serializable
    private class Blob(val address: String, val token: String, val expiresAt: Long) {
        override fun toString(): String = "Blob(<hidden>)"
    }

    private companion object {
        val BLOB = stringPreferencesKey("sealed_session")
    }
}
