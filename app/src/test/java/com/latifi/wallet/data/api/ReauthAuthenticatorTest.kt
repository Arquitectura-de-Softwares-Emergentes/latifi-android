package com.latifi.wallet.data.api

import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.auth.DefaultReauthCoordinator
import com.latifi.wallet.domain.auth.SignInOutcome
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakeLanguageController
import com.latifi.wallet.ui.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.Dispatcher as ServerDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ReauthAuthenticatorTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val address = "0x1111111111111111111111111111111111111111"
    private val sessions = InMemorySessionStore()
    private val coordinator = DefaultReauthCoordinator(OverlayController())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val profileJson =
        """{"address":"$address","displayName":"Sample User","createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:00:00Z"}"""

    private val rig = ApiTestRig(
        interceptors = listOf(AcceptLanguageInterceptor(FakeLanguageController(AppLanguage.English)), AuthInterceptor(sessions, clock)),
        authenticator = ReauthAuthenticator(coordinator, sessions, clock),
        clientTweak = { it.dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 64 }) },
    )

    @After
    fun tearDown() {
        scope.cancel()
        rig.close()
    }

    /** The server accepts only `Bearer new` and answers 401 to everything else. */
    private fun acceptOnlyTheNewToken() {
        rig.server.dispatcher = object : ServerDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == "Bearer new") {
                    MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(profileJson)
                } else {
                    MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                        .setBody("""{"status":401,"code":"UNAUTHORIZED","message":"expired"}""")
                }
        }
    }

    /** Plays the sign-in sheet: after [delayMillis] the wallet signs and the session holds `new`. */
    private fun answerSignIn(outcome: SignInOutcome = SignInOutcome.Signed, delayMillis: Long = 50) {
        scope.launch {
            coordinator.request.filterNotNull().collect {
                delay(delayMillis)
                if (outcome == SignInOutcome.Signed) sessions.save(Session(address, "new", now.plus(Duration.ofMinutes(15))))
                coordinator.report(outcome)
            }
        }
    }

    @Test
    fun aUnauthorizedAnswerRunsOneReauthenticationAndRetriesOnceWithTheNewToken() = runBlocking {
        acceptOnlyTheNewToken()
        answerSignIn()

        val response = rig.api.getProfile(address)

        assertTrue(response.isSuccessful)
        assertEquals("Sample User", response.body()?.displayName)
        assertEquals(1, coordinator.runs)
        assertEquals(2, rig.server.requestCount)
        assertNull(rig.server.takeRequest().getHeader("Authorization"))
        val retry = rig.server.takeRequest()
        assertEquals("Bearer new", retry.getHeader("Authorization"))
        assertNotNull(retry.getHeader(ReauthAuthenticator.RETRY_MARKER))
    }

    @Test
    fun aSecondUnauthorizedAnswerDoesNotLoop() = runBlocking {
        rig.server.dispatcher = object : ServerDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                    .setBody("""{"status":401,"code":"UNAUTHORIZED","message":"no"}""")
        }
        answerSignIn()

        val result = ApiProfileRepository(rig.api).get(address)

        assertEquals(AppError.Unauthorized, (result.exceptionOrNull() as AppException).error)
        assertEquals(1, coordinator.runs)
        assertEquals(2, rig.server.requestCount)
    }

    @Test
    fun theAuthOperationsNeverTriggerReauthentication() = runBlocking {
        rig.enqueueError(401, "INVALID_CREDENTIALS")
        rig.enqueueError(401, "UNAUTHORIZED")
        answerSignIn()

        val verify = ApiAuthRepository(rig.api).verify("id", address, "0x" + "ab".repeat(65))
        val challenge = ApiAuthRepository(rig.api).challenge(address)

        assertEquals(AppError.Unauthorized, (verify.exceptionOrNull() as AppException).error)
        assertEquals(AppError.Unauthorized, (challenge.exceptionOrNull() as AppException).error)
        assertEquals(0, coordinator.runs)
        assertEquals(2, rig.server.requestCount)
    }

    @Test
    fun aCancelledSignInEndsWithUnauthorizedAndNoRetry() = runBlocking {
        acceptOnlyTheNewToken()
        answerSignIn(outcome = SignInOutcome.Cancelled)

        val result = ApiProfileRepository(rig.api).get(address)

        assertEquals(AppError.Unauthorized, (result.exceptionOrNull() as AppException).error)
        assertEquals(1, coordinator.runs)
        assertEquals(1, rig.server.requestCount)
        assertNull(sessions.current())
    }

    @Test
    fun tenParallelUnauthorizedAnswersShareOneSignature() = runBlocking {
        acceptOnlyTheNewToken()
        answerSignIn(delayMillis = 300)

        val responses = (1..10).map { async(Dispatchers.IO) { rig.api.getProfile(address) } }.awaitAll()

        assertTrue(responses.all { it.isSuccessful })
        assertEquals(1, coordinator.runs)
    }

    @Test
    fun aRequestSentBeforeTheRenewalRetriesWithTheRenewedTokenAndAsksForNoSecondSignature() {
        sessionsHold("new")
        val stale = Request.Builder().url("https://api.example/api/v1/loans").header("Authorization", "Bearer old").build()
        val authenticator = ReauthAuthenticator(coordinator, sessions, clock)

        val retry = authenticator.authenticate(null, unauthorized(stale))

        assertEquals("Bearer new", retry?.header("Authorization"))
        assertEquals(0, coordinator.runs)
    }

    @Test
    fun aRejectedCurrentTokenStartsANewSignIn() {
        sessionsHold("new")
        answerSignIn()
        val request = Request.Builder().url("https://api.example/api/v1/loans").header("Authorization", "Bearer new").build()
        val authenticator = ReauthAuthenticator(coordinator, sessions, clock)

        val retry = authenticator.authenticate(null, unauthorized(request))

        // The server refused the token the app holds, so one new signature is needed.
        assertEquals(1, coordinator.runs)
        assertNotNull(retry)
    }

    @Test
    fun aRequestThatAlreadyCarriesTheRetryMarkerIsNotRetriedAgain() {
        val marked = Request.Builder().url("https://api.example/api/v1/loans")
            .header(ReauthAuthenticator.RETRY_MARKER, "1").build()

        assertNull(ReauthAuthenticator(coordinator, sessions, clock).authenticate(null, unauthorized(marked)))
        assertEquals(0, coordinator.runs)
    }

    private fun sessionsHold(token: String) = runBlocking {
        sessions.save(Session(address, token, now.plus(Duration.ofMinutes(15))))
    }

    private fun unauthorized(request: Request): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").build()
}
