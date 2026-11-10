package com.latifi.wallet.domain.auth

import com.latifi.wallet.data.fake.FakeAuthRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SignInUseCaseTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS

    private class Rig(val wallet: FakeWalletGateway, val auth: FakeAuthRepository, val sessions: InMemorySessionStore, val useCase: SignInUseCase)

    private fun TestScope.rig(script: FakeWalletScript = FakeWalletScript.AutoApprove(delayMillis = 100), auth: AuthRepository? = null): Rig {
        val wallet = FakeWalletGateway(scope = backgroundScope, initialState = WalletState.Connected(address), script = script)
        val fakeAuth = FakeAuthRepository(clock)
        val sessions = InMemorySessionStore()
        return Rig(wallet, fakeAuth, sessions, SignInUseCase(wallet, auth ?: fakeAuth, sessions, clock))
    }

    @Test
    fun successStoresTheSessionWithItsExpiry() = runTest {
        val rig = rig()

        val result = rig.useCase.signIn(address)

        assertEquals(SignInResult.Signed, result)
        val session = rig.sessions.current()
        assertNotNull(session)
        assertEquals(address, session!!.address)
        assertEquals(now.plus(Duration.ofSeconds(900)), session.expiresAt)
    }

    @Test
    fun theWalletSignsTheChallengeMessageByteForByte() = runTest {
        val odd = "  line one\nline two with accents é and a trailing newline\n"
        val challenge = Challenge("id-1", "nonce", odd, now, now.plusSeconds(300))
        val stub = object : AuthRepository {
            override suspend fun challenge(address: String) = Result.success(challenge)
            override suspend fun verify(challengeId: String, address: String, signature: String) =
                Result.success(Token("fake-token", 900, address))
        }
        val rig = rig(auth = stub)

        rig.useCase.signIn(address)

        val sent = rig.wallet.sentRequests.single().second as WalletRequest.PersonalSign
        assertEquals(odd, sent.message)
        assertEquals(odd.toByteArray(Charsets.UTF_8).toList(), sent.message.toByteArray(Charsets.UTF_8).toList())
    }

    @Test
    fun theMessageFromTheFakeApiReachesTheWalletUnchanged() = runTest {
        val rig = rig()

        rig.useCase.signIn(address)

        val sent = rig.wallet.sentRequests.single().second as WalletRequest.PersonalSign
        assertEquals(rig.auth.lastChallenge!!.message, sent.message)
    }

    @Test
    fun a401OnVerifyIsInvalidAndStoresNothing() = runTest {
        val rig = rig()
        rig.auth.failNextVerify = AppError.Unauthorized

        assertEquals(SignInResult.Invalid, rig.useCase.signIn(address))
        assertNull(rig.sessions.current())
    }

    @Test
    fun a401OnChallengeIsInvalidAndNeverAsksTheWallet() = runTest {
        val rig = rig()
        rig.auth.failNextChallenge = AppError.Unauthorized

        assertEquals(SignInResult.Invalid, rig.useCase.signIn(address))
        assertTrue(rig.wallet.sentRequests.isEmpty())
    }

    @Test
    fun a429CarriesTheRetryAfterSeconds() = runTest {
        val rig = rig()
        rig.auth.failNextVerify = AppError.RateLimited(12)

        assertEquals(SignInResult.RateLimited(12), rig.useCase.signIn(address))
    }

    @Test
    fun anOfflineApiIsANetworkError() = runTest {
        val rig = rig()
        rig.auth.failNextChallenge = AppError.Offline

        assertEquals(SignInResult.NetworkError, rig.useCase.signIn(address))
    }

    @Test
    fun aDeclinedRequestIsWalletDeclined() = runTest {
        val rig = rig(FakeWalletScript.Reject(100))
        assertEquals(SignInResult.WalletDeclined, rig.useCase.signIn(address))
        assertNull(rig.sessions.current())
    }

    @Test
    fun aLostWalletSessionIsReported() = runTest {
        val rig = rig(FakeWalletScript.SessionLost(100))
        assertEquals(SignInResult.WalletSessionLost, rig.useCase.signIn(address))
    }

    @Test
    fun aRetryAfterAFailureStartsFromANewChallenge() = runTest {
        val rig = rig()
        rig.auth.failNextVerify = AppError.Unauthorized
        rig.useCase.signIn(address)

        assertEquals(SignInResult.Signed, rig.useCase.signIn(address))
        assertEquals(2, rig.auth.challengeCalls)
    }

    @Test
    fun aLateSignatureAfterKeepWaitingStillCompletes() = runTest {
        val rig = rig(FakeWalletScript.NeverAnswer)
        val run = async { rig.useCase.signIn(address) }
        runCurrent()

        // Well past the 15 second panel, and past a "Keep waiting" press, nothing has answered.
        advanceTimeBy(45_000)
        runCurrent()
        assertTrue(run.isActive)

        rig.wallet.respond(rig.wallet.sentRequests.single().first, WalletOutcome.Signature(FakeWalletGateway.FAKE_SIGNATURE))

        assertEquals(SignInResult.Signed, run.await())
    }

    @Test
    fun onlyTheAnswerWithTheMatchingRequestIdIsAccepted() = runTest {
        val rig = rig(FakeWalletScript.NeverAnswer)
        val run = async { rig.useCase.signIn(address) }
        runCurrent()
        val id = rig.wallet.sentRequests.single().first

        rig.wallet.respond(id + 100, WalletOutcome.Signature(FakeWalletGateway.FAKE_SIGNATURE))
        runCurrent()
        assertTrue("an answer for another request must not finish the run", run.isActive)

        rig.wallet.respond(id, WalletOutcome.Rejected)
        assertEquals(SignInResult.WalletDeclined, run.await())
    }

    @Test
    fun aFailureWithoutAnAppErrorIsANetworkError() = runTest {
        val broken = object : AuthRepository {
            override suspend fun challenge(address: String) = Result.failure<Challenge>(IllegalStateException("boom"))
            override suspend fun verify(challengeId: String, address: String, signature: String) =
                Result.failure<Token>(AppException(AppError.Server))
        }
        val rig = rig(auth = broken)
        assertEquals(SignInResult.NetworkError, rig.useCase.signIn(address))
    }
}
