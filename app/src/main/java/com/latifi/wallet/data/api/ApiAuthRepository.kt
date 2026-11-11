package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.ChallengeRequestDto
import com.latifi.wallet.data.api.dto.VerifyRequestDto
import com.latifi.wallet.data.api.dto.toDomain
import com.latifi.wallet.domain.auth.AuthRepository
import com.latifi.wallet.domain.auth.Challenge
import com.latifi.wallet.domain.auth.Token

/**
 * `POST /auth/challenge` and `POST /auth/verify`. The address goes out as the wallet wrote it,
 * because the challenge text embeds it and the wallet signs that text byte for byte. A 401 on
 * verify is `Unauthorized` and never starts a re-authentication (the authenticator skips `/auth/`).
 */
class ApiAuthRepository(private val api: LatiFiApi) : AuthRepository {
    override suspend fun challenge(address: String): Result<Challenge> =
        apiResult(call = { api.issueChallenge(ChallengeRequestDto(address)) }, transform = { it.toDomain() })

    override suspend fun verify(challengeId: String, address: String, signature: String): Result<Token> =
        apiResult(
            call = { api.verifyChallenge(VerifyRequestDto(challengeId, address, signature)) },
            transform = { it.toDomain() },
        )
}
