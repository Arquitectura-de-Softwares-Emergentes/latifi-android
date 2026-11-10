package com.latifi.wallet.ui.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.session.SignOutHook
import com.latifi.wallet.domain.session.SignOutUseCase
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.support.FakeLanguageController
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.testConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val address = "0x52908400098527886E0F7030069857D2E4169EE7"
    private val at = Instant.parse("2026-11-01T00:00:00Z")

    private class CountingHook : SignOutHook {
        var runs = 0
        override suspend fun onSignOut() {
            runs += 1
        }
    }

    private class Rig(
        val vm: ProfileViewModel,
        val prefs: FakePrefsStore,
        val languages: FakeLanguageController,
        val sessions: InMemorySessionStore,
        val wallet: FakeWalletGateway,
        val hook: CountingHook,
        val handle: SavedStateHandle,
    )

    private fun TestScope.rig(
        withProfile: Boolean = true,
        handle: SavedStateHandle = SavedStateHandle(),
        language: AppLanguage = AppLanguage.English,
    ): Rig {
        val profiles = FakeProfileRepository()
        if (withProfile) profiles.seed(Profile(address, "Ana Torres", Contact(ContactType.EMAIL, "ana@example.com"), at, at))
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val prefs = FakePrefsStore(role = AppRole.Borrower)
        val languages = FakeLanguageController(language)
        val wallet = FakeWalletGateway(initialState = WalletState.Connected(address))
        val hook = CountingHook()
        val signOut = SignOutUseCase(wallet, sessions, prefs, setOf(hook))
        val vm = ProfileViewModel(handle, profiles, sessions, prefs, languages, signOut, testConfig())
        runCurrent()
        return Rig(vm, prefs, languages, sessions, wallet, hook, handle)
    }

    @Test
    fun theRowsReflectTheProfileAndTheShortAddress() = runTest {
        val state = rig().vm.state.value
        assertEquals("Ana Torres", state.displayName)
        assertEquals(Contact(ContactType.EMAIL, "ana@example.com"), state.contact)
        assertEquals("0x5290...9EE7", state.shortAddress)
        assertEquals(AppRole.Borrower, state.role)
        assertEquals(LocalCurrency.PEN, state.currency)
        assertEquals(AppMode.Demo, state.mode)
    }

    @Test
    fun withNoProfileTheDetailRowsStayEmpty() = runTest {
        val state = rig(withProfile = false).vm.state.value
        assertNull(state.displayName)
        assertNull(state.contact)
    }

    @Test
    fun shortenKeepsShortValuesWhole() {
        assertEquals("0x12", ProfileViewModel.shorten("0x12"))
        assertEquals("0x5290...9EE7", ProfileViewModel.shorten("0x52908400098527886E0F7030069857D2E4169EE7"))
    }

    @Test
    fun theRoleSwitchPersists() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileEvent.SwitchRole)
        runCurrent()
        assertEquals(AppRole.Lender, rig.prefs.role.first())
        assertEquals(AppRole.Lender, rig.vm.state.value.role)

        rig.vm.onEvent(ProfileEvent.SwitchRole)
        runCurrent()
        assertEquals(AppRole.Borrower, rig.prefs.role.first())
    }

    @Test
    fun theCurrencyChoicePersists() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileEvent.SelectCurrency(LocalCurrency.CLP))
        runCurrent()
        assertEquals(LocalCurrency.CLP, rig.prefs.currency.first())
        assertEquals(LocalCurrency.CLP, rig.vm.state.value.currency)
    }

    @Test
    fun copyAddressPutsTheAddressOnTheClipboardAndAnnouncesIt() = runTest {
        val rig = rig()
        rig.vm.effect.test {
            rig.vm.onEvent(ProfileEvent.CopyAddress)
            assertEquals(ProfileEffect.CopyToClipboard(address), awaitItem())
        }
        assertEquals(ProfileMessage.AddressCopied, rig.vm.state.value.message)

        rig.vm.onEvent(ProfileEvent.MessageShown)
        assertNull(rig.vm.state.value.message)
    }

    @Test
    fun choosingAnotherLanguageCallsTheControllerAndQueuesTheAnnouncement() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileEvent.SelectLanguage(AppLanguage.Spanish))
        assertEquals(listOf(AppLanguage.Spanish), rig.languages.requested)
        assertEquals(AppLanguage.Spanish, rig.vm.state.value.language)
        assertEquals(ProfileMessage.LanguageSpanish, rig.vm.state.value.message)
    }

    @Test
    fun choosingTheCurrentLanguageDoesNothing() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileEvent.SelectLanguage(AppLanguage.English))
        assertTrue(rig.languages.requested.isEmpty())
        assertNull(rig.vm.state.value.message)
    }

    @Test
    fun theLanguageAnnouncementSurvivesARecreationOfTheScreenState() = runTest {
        val first = rig()
        first.vm.onEvent(ProfileEvent.SelectLanguage(AppLanguage.Spanish))

        // A new view model on the same saved state is what the activity recreation leaves behind.
        val restored = rig(handle = first.handle, language = AppLanguage.Spanish).vm.state.value

        assertEquals(ProfileMessage.LanguageSpanish, restored.message)
        assertEquals(AppLanguage.Spanish, restored.language)
    }

    @Test
    fun theDialogOpensAndTheSafeChoiceKeepsEverything() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileEvent.AskDisconnect)
        assertTrue(rig.vm.state.value.disconnectDialogOpen)

        rig.vm.onEvent(ProfileEvent.KeepConnected)
        runCurrent()

        assertFalse(rig.vm.state.value.disconnectDialogOpen)
        assertEquals(0, rig.hook.runs)
        assertEquals(address, rig.sessions.current()?.address)
        assertEquals(AppRole.Borrower, rig.prefs.role.first())
    }

    @Test
    fun confirmingDisconnectRunsTheSignOutOnceAndLeavesLanguageAndCurrency() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileEvent.SelectCurrency(LocalCurrency.MXN))
        runCurrent()
        rig.vm.onEvent(ProfileEvent.AskDisconnect)

        rig.vm.effect.test {
            rig.vm.onEvent(ProfileEvent.ConfirmDisconnect)
            runCurrent()
            assertEquals(ProfileEffect.SignedOut, awaitItem())
        }

        assertEquals(1, rig.hook.runs)
        assertNull(rig.sessions.current())
        assertNull(rig.prefs.role.first())
        assertEquals(WalletState.Disconnected, rig.wallet.state.value)
        assertEquals(LocalCurrency.MXN, rig.prefs.currency.first())
        assertFalse(rig.vm.state.value.disconnectDialogOpen)
        assertTrue(rig.languages.requested.isEmpty())
    }

    @Test
    fun theNavigationEventsReachTheRoute() = runTest {
        val rig = rig()
        rig.vm.effect.test {
            rig.vm.onEvent(ProfileEvent.EditDetails)
            rig.vm.onEvent(ProfileEvent.OpenHowItWorks)
            rig.vm.onEvent(ProfileEvent.OpenTerms)
            rig.vm.onEvent(ProfileEvent.OpenPrivacy)
            rig.vm.onEvent(ProfileEvent.OpenRatesProvider)
            assertEquals(ProfileEffect.OpenForm, awaitItem())
            assertEquals(ProfileEffect.OpenHowItWorks, awaitItem())
            assertEquals(ProfileEffect.OpenLink(testConfig().termsUrl), awaitItem())
            assertEquals(ProfileEffect.OpenLink(testConfig().privacyUrl), awaitItem())
            assertEquals(ProfileEffect.OpenLink("https://www.exchangerate-api.com"), awaitItem())
        }
    }
}
