package com.latifi.wallet.data.api

import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakeLanguageController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AuthInterceptorTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sessions = InMemorySessionStore()
    private val language = FakeLanguageController(AppLanguage.English)
    private val rig = ApiTestRig(listOf(AcceptLanguageInterceptor(language), AuthInterceptor(sessions, clock)))
    private val address = "0x1111111111111111111111111111111111111111"

    @After
    fun tearDown() = rig.close()

    private fun call() = runBlocking {
        rig.enqueueError(404, "PROFILE_NOT_FOUND")
        rig.api.getProfile(address)
        rig.server.takeRequest()
    }

    @Test
    fun aTokenThatHasNotExpiredIsAttached() = runBlocking {
        sessions.save(Session(address, "tok-1", now.plus(Duration.ofMinutes(10))))

        assertEquals("Bearer tok-1", call().getHeader("Authorization"))
    }

    @Test
    fun anExpiredTokenIsNeverSentSoAPublicReadStaysAnonymous() = runBlocking {
        sessions.save(Session(address, "tok-1", now.minusSeconds(1)))

        assertNull(call().getHeader("Authorization"))
    }

    @Test
    fun aTokenThatExpiresExactlyNowIsNotSent() = runBlocking {
        sessions.save(Session(address, "tok-1", now))

        assertNull(call().getHeader("Authorization"))
    }

    @Test
    fun noSessionMeansNoAuthorizationHeader() = runBlocking {
        assertNull(call().getHeader("Authorization"))
    }

    @Test
    fun theAuthOperationsNeverCarryAToken() = runBlocking {
        sessions.save(Session(address, "tok-1", now.plus(Duration.ofMinutes(10))))
        rig.enqueueError(401, "INVALID_CREDENTIALS")
        rig.enqueueError(429, "TOO_MANY_REQUESTS")

        rig.api.verifyChallenge(com.latifi.wallet.data.api.dto.VerifyRequestDto("id", address, "0x00"))
        rig.api.issueChallenge(com.latifi.wallet.data.api.dto.ChallengeRequestDto(address))

        assertNull(rig.server.takeRequest().getHeader("Authorization"))
        assertNull(rig.server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun acceptLanguageFollowsTheAppLanguage() = runBlocking {
        language.set(AppLanguage.English)
        assertEquals("en-US", call().getHeader("Accept-Language"))

        language.set(AppLanguage.Spanish)
        assertEquals("es-419", call().getHeader("Accept-Language"))
    }
}
