package com.latifi.wallet.ui.connect

import app.cash.turbine.test
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.auth.SignInOutcome
import com.latifi.wallet.domain.auth.SignInReason
import com.latifi.wallet.domain.auth.SignInRequest
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.wallet.WalletFailure
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.testConfig
import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Records every call and lets the test choose when the sign-in ends. */
    private class RecordingReauth : ReauthCoordinator {
        val reasons = mutableListOf<SignInReason>()
        var pending = CompletableDeferred<SignInOutcome>()
        override val request: StateFlow<SignInRequest?> = MutableStateFlow(null)

        override suspend fun ensureSession(reason: SignInReason): SignInOutcome {
            reasons += reason
            return pending.await()
        }

        override suspend fun report(outcome: SignInOutcome) = Unit
    }

    private class Rig(
        val wallet: FakeWalletGateway,
        val reauth: RecordingReauth,
        val overlays: OverlayController,
        val vm: ConnectViewModel,
    )

    private fun kotlinx.coroutines.test.TestScope.rig(
        initial: WalletState = WalletState.Disconnected,
        role: AppRole? = null,
    ): Rig {
        val wallet = FakeWalletGateway(scope = backgroundScope, initialState = initial, connectDelayMillis = 500)
        val reauth = RecordingReauth()
        val overlays = OverlayController()
        val vm = ConnectViewModel(wallet, reauth, FakePrefsStore(role = role), overlays, testConfig())
        runCurrent()
        return Rig(wallet, reauth, overlays, vm)
    }

    @Test
    fun startsIdleAndKeepsTheReturningTitleFlag() = runTest {
        val rig = rig()
        assertEquals(ConnectPhase.Idle, rig.vm.state.value.phase)

        rig.vm.setReturning(true)
        assertEquals(true, rig.vm.state.value.returning)
    }

    @Test
    fun aWalletWithoutAProjectIdShowsTheNotConfiguredState() = runTest {
        val rig = rig(initial = WalletState.NotConfigured)
        assertEquals(ConnectPhase.NotConfigured, rig.vm.state.value.phase)
    }

    @Test
    fun connectingThenConnectedSignsInExactlyOnce() = runTest {
        val rig = rig()

        rig.vm.onEvent(ConnectEvent.Connect)
        runCurrent()
        assertEquals(ConnectPhase.Waiting, rig.vm.state.value.phase)
        assertEquals(1, rig.wallet.pickerOpened)

        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(SignInReason.FirstSignIn), rig.reauth.reasons)

        // A second Connected value, or another tap while signing in, must not start another run.
        rig.wallet.setState(WalletState.Connected(rig.wallet.demoAddress))
        rig.vm.onEvent(ConnectEvent.Connect)
        runCurrent()
        assertEquals(1, rig.reauth.reasons.size)
    }

    @Test
    fun aSignedOutcomeGoesToRoleSelectionWhenThereIsNoRole() = runTest {
        val rig = rig(initial = WalletState.Connected("0xabc"))
        rig.vm.effect.test {
            rig.reauth.pending.complete(SignInOutcome.Signed)
            runCurrent()
            assertEquals(ConnectEffect.GoToRole, awaitItem())
        }
    }

    @Test
    fun aSignedOutcomeGoesStraightToTheTabsWhenARoleIsStored() = runTest {
        val rig = rig(initial = WalletState.Connected("0xabc"), role = AppRole.Lender)
        rig.vm.effect.test {
            rig.reauth.pending.complete(SignInOutcome.Signed)
            runCurrent()
            assertEquals(ConnectEffect.GoToMain, awaitItem())
        }
    }

    @Test
    fun aFailedConnectionShowsThePanelAndRetryOpensThePickerAgain() = runTest {
        val rig = rig(initial = WalletState.Failed(WalletFailure.NotInstalled))
        assertEquals(ConnectPhase.Failed, rig.vm.state.value.phase)

        rig.vm.onEvent(ConnectEvent.Connect)
        runCurrent()

        assertEquals(1, rig.wallet.pickerOpened)
        assertEquals(ConnectPhase.Waiting, rig.vm.state.value.phase)
    }

    @Test
    fun aCancelledSignInKeepsTheWalletAndTheNextTapSignsInWithoutThePicker() = runTest {
        val rig = rig(initial = WalletState.Connected("0xabc"))
        rig.reauth.pending.complete(SignInOutcome.Cancelled)
        runCurrent()
        assertEquals(ConnectPhase.Idle, rig.vm.state.value.phase)

        rig.reauth.pending = CompletableDeferred()
        rig.vm.onEvent(ConnectEvent.Connect)
        runCurrent()

        assertEquals(0, rig.wallet.pickerOpened)
        assertEquals(2, rig.reauth.reasons.size)
    }

    @Test
    fun noWalletOpensTheHelpSheet() = runTest {
        val rig = rig()
        rig.vm.onEvent(ConnectEvent.NoWallet)
        assertEquals(Overlays.WALLET_HELP, rig.overlays.current.value)
    }

    @Test
    fun theLegalLinksComeFromTheConfig() = runTest {
        val rig = rig()
        rig.vm.effect.test {
            rig.vm.onEvent(ConnectEvent.OpenTerms)
            rig.vm.onEvent(ConnectEvent.OpenPrivacy)
            assertEquals(ConnectEffect.OpenLink(testConfig().termsUrl), awaitItem())
            assertEquals(ConnectEffect.OpenLink(testConfig().privacyUrl), awaitItem())
        }
    }
}
