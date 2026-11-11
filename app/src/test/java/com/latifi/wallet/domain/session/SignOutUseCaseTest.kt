package com.latifi.wallet.domain.session

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.support.FakePrefsStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class SignOutUseCaseTest {
    private val address = "0xAbC0000000000000000000000000000000000001"

    private class CountingHook : SignOutHook {
        var runs = 0
        override suspend fun onSignOut() {
            runs += 1
        }
    }

    private class Rig(
        val wallet: FakeWalletGateway,
        val sessions: InMemorySessionStore,
        val prefs: FakePrefsStore,
        val hook: CountingHook,
        val useCase: SignOutUseCase,
    )

    private fun rig(hooks: (CountingHook) -> Set<SignOutHook> = { setOf(it) }): Rig = runBlocking {
        val wallet = FakeWalletGateway(initialState = WalletState.Connected(address))
        val sessions = InMemorySessionStore().also {
            it.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z")))
        }
        val prefs = FakePrefsStore(role = AppRole.Lender, currency = LocalCurrency.COP)
        val hook = CountingHook()
        Rig(wallet, sessions, prefs, hook, SignOutUseCase(wallet, sessions, prefs, hooks(hook)))
    }

    @Test
    fun afterSignOutTheSessionAndTheRoleAreGoneAndTheWalletIsDisconnected() = runBlocking {
        val rig = rig()

        rig.useCase()

        assertNull(rig.sessions.current())
        assertNull(rig.prefs.role.first())
        assertEquals(WalletState.Disconnected, rig.wallet.state.value)
    }

    @Test
    fun everyHookRunsOnce() = runBlocking {
        val second = CountingHook()
        val rig = rig { setOf(it, second) }

        rig.useCase()

        assertEquals(1, rig.hook.runs)
        assertEquals(1, second.runs)
    }

    @Test
    fun languageAndCurrencyStay() = runBlocking {
        val rig = rig()

        rig.useCase()

        assertEquals(LocalCurrency.COP, rig.prefs.currency.first())
    }

    @Test
    fun anEmptyHookSetIsFine() = runBlocking {
        val rig = rig { emptySet() }
        rig.useCase()
        assertNull(rig.sessions.current())
    }

    @Test
    fun aFailingHookDoesNotStopTheOthers() = runBlocking {
        val failing = object : SignOutHook {
            override suspend fun onSignOut() = throw IllegalStateException("boom")
        }
        val rig = rig { linkedSetOf(failing, it) }

        rig.useCase()

        assertEquals(1, rig.hook.runs)
        assertNull(rig.sessions.current())
    }

    @Test
    fun aWalletThatFailsToDisconnectStillLeavesNoCredentialsBehind() = runBlocking {
        val base = FakeWalletGateway(initialState = WalletState.Connected(address))
        val failing = object : WalletGateway by base {
            override suspend fun disconnect() = throw IllegalStateException("wallet is gone")
        }
        val sessions = InMemorySessionStore().also {
            it.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z")))
        }
        val prefs = FakePrefsStore(role = AppRole.Borrower)
        val hook = CountingHook()

        try {
            SignOutUseCase(failing, sessions, prefs, setOf(hook))()
            fail("the wallet failure should reach the caller")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("wallet"))
        }

        assertNull(sessions.current())
        assertNull(prefs.role.first())
        assertEquals(1, hook.runs)
    }

    @Test
    fun aWalletThatNeverAnswersStillLeavesNoCredentialsBehindAfterTheTimeout() = runTest {
        val base = FakeWalletGateway(initialState = WalletState.Connected(address))
        val hanging = object : WalletGateway by base {
            override suspend fun disconnect() = awaitCancellation()
        }
        val sessions = InMemorySessionStore().also {
            it.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z")))
        }
        val prefs = FakePrefsStore(role = AppRole.Borrower)
        val hook = CountingHook()

        SignOutUseCase(hanging, sessions, prefs, setOf(hook))()

        assertEquals(SignOutUseCase.WALLET_DISCONNECT_TIMEOUT_MILLIS, currentTime)
        assertNull(sessions.current())
        assertNull(prefs.role.first())
        assertEquals(1, hook.runs)
    }
}
