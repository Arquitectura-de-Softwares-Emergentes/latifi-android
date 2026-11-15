package com.latifi.wallet.data.fake

import com.latifi.wallet.domain.auth.AuthRepository
import com.latifi.wallet.domain.auth.Challenge
import com.latifi.wallet.domain.auth.Token
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * A stand-in for the API challenge and verify calls. The message follows the EIP-4361 layout of
 * the real API, the challenge lives 5 minutes and is consumed once, and the token lives 900
 * seconds. Tokens are fake strings. A test or a demo script can queue a 401 or a 429.
 */
class FakeAuthRepository(private val clock: Clock = Clock.systemUTC()) : AuthRepository {
    private val open = mutableMapOf<String, Challenge>()

    /** The next call to [challenge] or [verify] fails with this error, once. */
    var failNextChallenge: AppError? = null
    var failNextVerify: AppError? = null

    var challengeCalls = 0
        private set
    var verifyCalls = 0
        private set

    /** The last challenge issued. A test compares it with what the wallet was asked to sign. */
    var lastChallenge: Challenge? = null
        private set

    override suspend fun challenge(address: String): Result<Challenge> {
        challengeCalls += 1
        failNextChallenge?.let { failNextChallenge = null; return Result.failure(AppException(it)) }
        val now = clock.instant()
        val expires = now.plus(Duration.ofMinutes(5))
        val id = UUID.randomUUID().toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val message = buildString {
            append("latifi.invalid wants you to sign in with your Ethereum account:\n")
            append(address).append("\n\n")
            append("Sign in to LatiFi. This request does not trigger a blockchain transaction or cost any fee.\n\n")
            append("URI: https://latifi.invalid\n")
            append("Version: 1\n")
            append("Chain ID: 80002\n")
            append("Nonce: ").append(nonce).append("\n")
            append("Issued At: ").append(now).append("\n")
            append("Expiration Time: ").append(expires)
        }
        val challenge = Challenge(id, nonce, message, now, expires)
        open[id] = challenge
        lastChallenge = challenge
        return Result.success(challenge)
    }

    override suspend fun verify(challengeId: String, address: String, signature: String): Result<Token> {
        verifyCalls += 1
        failNextVerify?.let { failNextVerify = null; return Result.failure(AppException(it)) }
        val challenge = open.remove(challengeId)
        val valid = challenge != null &&
            clock.instant().isBefore(challenge.expiresAt) &&
            SIGNATURE_SHAPE.matches(signature)
        if (!valid) return Result.failure(AppException(AppError.Unauthorized))
        return Result.success(Token(accessToken = "fake-token-$verifyCalls", expiresInSeconds = TOKEN_SECONDS, address = address))
    }

    companion object {
        const val TOKEN_SECONDS = 900L
        private val SIGNATURE_SHAPE = Regex("^0x[0-9a-fA-F]{130}$")
    }
}
