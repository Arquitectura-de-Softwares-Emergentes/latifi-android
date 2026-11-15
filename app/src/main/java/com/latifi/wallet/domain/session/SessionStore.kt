package com.latifi.wallet.domain.session

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** What the app keeps about a signed-in person: the address and the short lived token. */
data class Session(
    val address: String,
    val token: String,
    val expiresAt: Instant,
) {
    // The token is a secret, so it never shows up in a log line through toString.
    override fun toString(): String = "Session(address=<hidden>, token=<hidden>, expiresAt=$expiresAt)"
}

interface SessionStore {
    val session: Flow<Session?>

    suspend fun save(session: Session)

    suspend fun clear()

    suspend fun current(): Session?
}
