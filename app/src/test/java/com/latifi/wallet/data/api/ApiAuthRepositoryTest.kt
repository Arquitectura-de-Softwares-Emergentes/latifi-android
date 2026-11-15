package com.latifi.wallet.data.api

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ApiAuthRepositoryTest {
    private val rig = ApiTestRig()
    private val repository = ApiAuthRepository(rig.api)
    private val address = "0x52908400098527886E0F7030069857D2E4169EE7"

    @After
    fun tearDown() = rig.close()

    @Test
    fun challengePostsTheAddressAndReturnsTheMessageUntouched() = runBlocking {
        rig.enqueueJson(
            """{"challengeId":"3f2b8c1e-5d4a-4c7b-9a10-2e6f7d8c9b01","nonce":"9c1d4f7a2b8e4d3f5a6b7c8d9e0f1a2b","message":"line one\n$address\n\nNonce: 9c1d","issuedAt":"2026-01-01T12:00:00Z","expiresAt":"2026-01-01T12:05:00Z"}""",
        )

        val challenge = repository.challenge(address).getOrThrow()

        val request = rig.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/challenge", request.path)
        assertEquals(address, Json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("address").jsonPrimitive.content)
        assertEquals("line one\n$address\n\nNonce: 9c1d", challenge.message)
        assertEquals(Instant.parse("2026-01-01T12:05:00Z"), challenge.expiresAt)
    }

    @Test
    fun verifyPostsTheThreeFieldsAndReturnsTheToken() = runBlocking {
        rig.enqueueJson("""{"accessToken":"a.b.c","tokenType":"Bearer","expiresIn":900,"address":"$address"}""")

        val token = repository.verify("3f2b8c1e-5d4a-4c7b-9a10-2e6f7d8c9b01", address, "0x" + "ab".repeat(65)).getOrThrow()

        val request = rig.server.takeRequest()
        assertEquals("/api/v1/auth/verify", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("challengeId", "address", "signature"), body.keys)
        assertEquals("a.b.c", token.accessToken)
        assertEquals(900L, token.expiresInSeconds)
        assertEquals(address.lowercase(), token.address)
    }

    @Test
    fun aRejectedSignatureIsUnauthorizedAndNotAnExpiredSession() = runBlocking {
        rig.enqueueError(401, "INVALID_CREDENTIALS")

        val failure = repository.verify("id", address, "0x" + "ab".repeat(65)).exceptionOrNull() as AppException

        assertEquals(AppError.Unauthorized, failure.error)
        assertEquals(1, rig.server.requestCount)
    }

    @Test
    fun aRateLimitedChallengeCarriesRetryAfter() = runBlocking {
        rig.enqueueError(429, "TOO_MANY_REQUESTS", mapOf("Retry-After" to "30"))

        val failure = repository.challenge(address).exceptionOrNull() as AppException

        assertEquals(AppError.RateLimited(30), failure.error)
    }

    @Test
    fun aChallengeBodyWithoutAMessageFailsAsAServerProblem() = runBlocking {
        rig.enqueueJson("""{"challengeId":"x"}""")

        val failure = repository.challenge(address).exceptionOrNull() as AppException

        assertEquals(AppError.Server, failure.error)
    }
}
