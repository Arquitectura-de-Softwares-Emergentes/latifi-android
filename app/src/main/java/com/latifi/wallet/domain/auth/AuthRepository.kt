package com.latifi.wallet.domain.auth

import java.time.Instant

/**
 * The two unauthenticated calls of the sign-in. Both return a [Result] whose failure holds an
 * `AppException` with an `AppError` (Unauthorized for a 401, RateLimited for a 429, and so on).
 */
interface AuthRepository {
    suspend fun challenge(address: String): Result<Challenge>

    suspend fun verify(challengeId: String, address: String, signature: String): Result<Token>
}

/** [message] is the EIP-4361 text the wallet signs without any change. */
data class Challenge(
    val challengeId: String,
    val nonce: String,
    val message: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

data class Token(
    val accessToken: String,
    val expiresInSeconds: Long,
    val address: String,
)
