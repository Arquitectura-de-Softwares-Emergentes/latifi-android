package com.latifi.wallet.data.session

import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Keeps the session in memory only. The session is gone when the process ends, so a returning
 * person signs in again. Plan 05-14 adds the encrypted store behind the same interface.
 */
class InMemorySessionStore : SessionStore {
    private val state = MutableStateFlow<Session?>(null)

    override val session: Flow<Session?> = state

    override suspend fun save(session: Session) {
        state.value = session
    }

    override suspend fun clear() {
        state.value = null
    }

    override suspend fun current(): Session? = state.value
}
