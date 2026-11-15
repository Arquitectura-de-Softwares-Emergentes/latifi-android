package com.latifi.wallet.ui.signin

import com.latifi.wallet.data.fake.FakeAuthRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.auth.DefaultReauthCoordinator
import com.latifi.wallet.domain.auth.SignInOutcome
import com.latifi.wallet.domain.auth.SignInReason
import com.latifi.wallet.domain.auth.SignInUseCase
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.ui.overlay.OverlayController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Rig(
        val vm: SignInViewModel,
        val wallet: FakeWalletGateway,
        val auth: FakeAuthRepository,
        val sessions: InMemorySessionStore,
        val coordinator: DefaultReauthCoordinator,
        val overlays: OverlayController,
    )

    private fun TestScope.rig(
        script: FakeWalletScript = FakeWalletScript.AutoApprove(delayMillis = 100),
        reason: SignInReason = SignInReason.FirstSignIn,
        walletState: WalletState = WalletState.Connected(FakeWalletGateway.DEMO_ADDRESS),
    ): Rig {
        val clock = Clock.systemUTC()
        val wallet = FakeWalletGateway(scope = backgroundScope, initialState = walletState, script = script)
        val auth = FakeAuthRepository(clock)
        val sessions = InMemorySessionStore()
        val overlays = OverlayController()
        val coordinator = DefaultReauthCoordinator(overlays)
        val vm = SignInViewModel(SignInUseCase(wallet, auth, sessions, clock), wallet, coordinator)
        vm.start(reason)
        runCurrent()
        return Rig(vm, wallet, auth, sessions, coordinator, overlays)
    }

    @Test
    fun startsReadyWithTheFirstSignInBody() = runTest {
        val state = rig().vm.state.value
        assertEquals(SignInPhase.Ready, state.phase)
        assertEquals(false, state.reauth)
    }

    @Test
    fun aSessionThatEndedUsesTheReauthBody() = runTest {
        assertEquals(true, rig(reason = SignInReason.SessionExpired).vm.state.value.reauth)
    }

    @Test
    fun confirmWaitsThenReportsSignedToTheCoordinator() = runTest {
        val rig = rig()
        val outcome = async { rig.coordinator.ensureSession(SignInReason.FirstSignIn) }
        runCurrent()

        rig.vm.onEvent(SignInEvent.Confirm)
        runCurrent()
        assertEquals(SignInPhase.Waiting, rig.vm.state.value.phase)

        advanceTimeBy(100)
        runCurrent()

        assertEquals(SignInOutcome.Signed, outcome.await())
        assertEquals(FakeWalletGateway.DEMO_ADDRESS, rig.sessions.current()?.address)
        assertNull(rig.overlays.current.value)
    }

    @Test
    fun theTimeoutPanelAppearsAtFifteenSecondsAndKeepWaitingRestartsIt() = runTest {
        val rig = rig(FakeWalletScript.NeverAnswer)
        rig.vm.onEvent(SignInEvent.Confirm)
        runCurrent()

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(SignInPhase.Waiting, rig.vm.state.value.phase)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(SignInPhase.TimedOut, rig.vm.state.value.phase)

        rig.vm.onEvent(SignInEvent.KeepWaiting)
        runCurrent()
        assertEquals(SignInPhase.Waiting, rig.vm.state.value.phase)

        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(SignInPhase.TimedOut, rig.vm.state.value.phase)
    }

    @Test
    fun aLateSignatureAfterTheTimeoutPanelStillSignsIn() = runTest {
        val rig = rig(FakeWalletScript.NeverAnswer)
        val outcome = async { rig.coordinator.ensureSession(SignInReason.FirstSignIn) }
        runCurrent()
        rig.vm.onEvent(SignInEvent.Confirm)
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(SignInPhase.TimedOut, rig.vm.state.value.phase)
        rig.vm.onEvent(SignInEvent.KeepWaiting)

        rig.wallet.respond(rig.wallet.sentRequests.single().first, WalletOutcome.Signature(FakeWalletGateway.FAKE_SIGNATURE))
        runCurrent()

        assertEquals(SignInOutcome.Signed, outcome.await())
    }

    @Test
    fun a401ShowsTheInvalidState() = runTest {
        val rig = rig()
        rig.auth.failNextVerify = AppError.Unauthorized
        rig.vm.onEvent(SignInEvent.Confirm)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(SignInPhase.Invalid, rig.vm.state.value.phase)
    }

    @Test
    fun aDeclinedRequestShowsTheDeclinedState() = runTest {
        val rig = rig(FakeWalletScript.Reject(100))
        rig.vm.onEvent(SignInEvent.Confirm)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(SignInPhase.Declined, rig.vm.state.value.phase)
    }

    @Test
    fun aLostWalletSessionOffersReconnect() = runTest {
        val rig = rig(FakeWalletScript.SessionLost(100))
        rig.vm.onEvent(SignInEvent.Confirm)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(SignInPhase.SessionLost, rig.vm.state.value.phase)
    }

    @Test
    fun confirmingWithNoConnectedWalletShowsSessionLost() = runTest {
        val rig = rig(walletState = WalletState.Disconnected)
        rig.vm.onEvent(SignInEvent.Confirm)
        runCurrent()
        assertEquals(SignInPhase.SessionLost, rig.vm.state.value.phase)
    }

    @Test
    fun anOfflineApiShowsTheOfflineState() = runTest {
        val rig = rig()
        rig.auth.failNextChallenge = AppError.Offline
        rig.vm.onEvent(SignInEvent.Confirm)
        runCurrent()
        assertEquals(SignInPhase.Offline, rig.vm.state.value.phase)
    }

    @Test
    fun theRateLimitCountdownChangesEveryFiveSecondsAndEnablesTheButtonAtZero() = runTest {
        val rig = rig()
        rig.auth.failNextVerify = AppError.RateLimited(12)
        rig.vm.onEvent(SignInEvent.Confirm)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(SignInUiState(SignInPhase.RateLimited, reauth = false, secondsLeft = 12), rig.vm.state.value)

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals("no change before 5 seconds", 12, rig.vm.state.value.secondsLeft)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(7, rig.vm.state.value.secondsLeft)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, rig.vm.state.value.secondsLeft)
        assertEquals(SignInPhase.RateLimited, rig.vm.state.value.phase)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(SignInPhase.Ready, rig.vm.state.value.phase)
    }

    @Test
    fun aRateLimitWithoutAHeaderUsesTheDefaultWait() = runTest {
        val rig = rig()
        rig.auth.failNextVerify = AppError.RateLimited(null)
        rig.vm.onEvent(SignInEvent.Confirm)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(SignInViewModel.DEFAULT_RETRY_SECONDS, rig.vm.state.value.secondsLeft)
    }

    @Test
    fun cancelReportsCancelledAndStoresNothing() = runTest {
        val rig = rig(FakeWalletScript.NeverAnswer)
        val outcome = async { rig.coordinator.ensureSession(SignInReason.FirstSignIn) }
        runCurrent()
        rig.vm.onEvent(SignInEvent.Confirm)
        runCurrent()

        rig.vm.onEvent(SignInEvent.Cancel)
        runCurrent()

        assertEquals(SignInOutcome.Cancelled, outcome.await())
        assertNull(rig.sessions.current())
        assertNull(rig.overlays.current.value)
    }

    @Test
    fun reconnectReopensThePickerAndReturnsToReady() = runTest {
        val rig = rig(FakeWalletScript.NeverAnswer)
        rig.vm.onEvent(SignInEvent.Confirm)
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()

        rig.vm.onEvent(SignInEvent.Reconnect)
        runCurrent()

        assertEquals(1, rig.wallet.pickerOpened)
        assertEquals(SignInPhase.Ready, rig.vm.state.value.phase)
    }
}
