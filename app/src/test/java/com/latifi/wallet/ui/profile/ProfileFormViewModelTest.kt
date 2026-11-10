package com.latifi.wallet.ui.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.profile.ContactError
import com.latifi.wallet.domain.profile.NameError
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.testConfig
import com.latifi.wallet.ui.nav.RouteArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ProfileFormViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val address = "0xAbC0000000000000000000000000000000000001"

    private class Rig(
        val vm: ProfileFormViewModel,
        val profiles: FakeProfileRepository,
        val handle: SavedStateHandle,
    )

    private fun TestScope.rig(
        handle: SavedStateHandle = SavedStateHandle(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
        role: AppRole? = AppRole.Borrower,
        signedIn: Boolean = true,
    ): Rig {
        val sessions = InMemorySessionStore()
        if (signedIn) {
            // The store is in memory and its save does not suspend in practice, so a direct call is safe.
            kotlinx.coroutines.runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        }
        val vm = ProfileFormViewModel(handle, profiles, sessions, FakePrefsStore(role = role), testConfig())
        runCurrent()
        return Rig(vm, profiles, handle)
    }

    private fun Rig.fillValid() {
        vm.onEvent(ProfileFormEvent.NameChanged("Ana Torres"))
        vm.onEvent(ProfileFormEvent.ContactChanged("ana@example.com"))
    }

    @Test
    fun startsBlankWithEmailSelected() = runTest {
        val rig = rig()
        val state = rig.vm.state.value
        assertEquals("", state.name)
        assertEquals("", state.contact)
        assertEquals(ContactType.EMAIL, state.contactType)
        assertEquals(0, state.errorCount)
        assertEquals(AppRole.Borrower, state.role)
    }

    @Test
    fun theGateFlagComesFromTheRouteArgument() = runTest {
        assertFalse(rig().vm.state.value.fromGate)
        assertTrue(rig(handle = SavedStateHandle(mapOf(RouteArgs.GATE to true))).vm.state.value.fromGate)
    }

    @Test
    fun submitWithTwoBadFieldsShowsBothErrorsAndTheCount() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.ContactChanged("nope"))

        rig.vm.onEvent(ProfileFormEvent.Submit)

        val state = rig.vm.state.value
        assertEquals(NameError.Required, state.nameError)
        assertEquals(ContactError.Invalid, state.contactError)
        assertEquals(2, state.errorCount)
        assertTrue(rig.profiles.upserts.isEmpty())
    }

    @Test
    fun submitWithOneBadFieldCountsOne() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.NameChanged("Ana"))
        rig.vm.onEvent(ProfileFormEvent.ContactChanged("nope"))

        rig.vm.onEvent(ProfileFormEvent.Submit)

        assertNull(rig.vm.state.value.nameError)
        assertEquals(1, rig.vm.state.value.errorCount)
    }

    @Test
    fun focusGoesToTheFirstInvalidField() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.Submit)
        assertEquals(ProfileField.Name, rig.vm.state.value.focusRequest?.field)

        rig.vm.onEvent(ProfileFormEvent.FocusHandled)
        assertNull(rig.vm.state.value.focusRequest)

        rig.vm.onEvent(ProfileFormEvent.NameChanged("Ana"))
        rig.vm.onEvent(ProfileFormEvent.Submit)
        assertEquals(ProfileField.Contact, rig.vm.state.value.focusRequest?.field)
    }

    @Test
    fun aSecondRejectedSubmitAsksForFocusAgain() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.Submit)
        val first = rig.vm.state.value.focusRequest
        rig.vm.onEvent(ProfileFormEvent.Submit)
        val second = rig.vm.state.value.focusRequest
        assertTrue(first != second)
    }

    @Test
    fun blurValidatesOnlyTheFieldThatWasLeft() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.Blurred(ProfileField.Name))
        assertEquals(NameError.Required, rig.vm.state.value.nameError)
        assertNull(rig.vm.state.value.contactError)

        rig.vm.onEvent(ProfileFormEvent.ContactChanged("x"))
        rig.vm.onEvent(ProfileFormEvent.Blurred(ProfileField.Contact))
        assertEquals(ContactError.Invalid, rig.vm.state.value.contactError)
    }

    @Test
    fun anErrorClearsAsSoonAsTheValueBecomesValid() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.Blurred(ProfileField.Name))
        rig.vm.onEvent(ProfileFormEvent.NameChanged("Ana"))
        assertNull(rig.vm.state.value.nameError)
    }

    @Test
    fun changingTheTypeClearsTheOldContactError() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.ContactChanged("nope"))
        rig.vm.onEvent(ProfileFormEvent.Blurred(ProfileField.Contact))
        assertEquals(ContactError.Invalid, rig.vm.state.value.contactError)

        rig.vm.onEvent(ProfileFormEvent.TypeChanged(ContactType.PHONE))

        assertEquals(ContactType.PHONE, rig.vm.state.value.contactType)
        assertNull(rig.vm.state.value.contactError)
    }

    @Test
    fun valuesSurviveASavedStateRestore() = runTest {
        val first = rig()
        first.vm.onEvent(ProfileFormEvent.NameChanged("Ana Torres"))
        first.vm.onEvent(ProfileFormEvent.TypeChanged(ContactType.PHONE))
        first.vm.onEvent(ProfileFormEvent.ContactChanged("+51987654321"))

        val restored = rig(handle = first.handle).vm.state.value

        assertEquals("Ana Torres", restored.name)
        assertEquals(ContactType.PHONE, restored.contactType)
        assertEquals("+51987654321", restored.contact)
    }

    @Test
    fun aValidSaveFromTheGateWritesTheResultKeyAndPops() = runTest {
        val rig = rig(handle = SavedStateHandle(mapOf(RouteArgs.GATE to true)))
        rig.fillValid()

        rig.vm.effect.test {
            rig.vm.onEvent(ProfileFormEvent.Submit)
            runCurrent()
            assertEquals(ProfileFormEffect.Saved("profile_gate_result"), awaitItem())
        }
        val saved = rig.profiles.upserts.single()
        assertEquals(address, saved.first)
        assertEquals("Ana Torres", saved.second.displayName)
        assertEquals(Contact(ContactType.EMAIL, "ana@example.com"), saved.second.contact)
    }

    @Test
    fun aSaveFromTheProfileTabWritesNoResultKey() = runTest {
        val rig = rig()
        rig.fillValid()

        rig.vm.effect.test {
            rig.vm.onEvent(ProfileFormEvent.Submit)
            runCurrent()
            assertEquals(ProfileFormEffect.Saved(null), awaitItem())
        }
    }

    @Test
    fun theSavedValuesAreTrimmed() = runTest {
        val rig = rig()
        rig.vm.onEvent(ProfileFormEvent.NameChanged("  Ana Torres "))
        rig.vm.onEvent(ProfileFormEvent.ContactChanged(" ana@example.com "))
        rig.vm.onEvent(ProfileFormEvent.Submit)
        runCurrent()
        val saved = rig.profiles.upserts.single().second
        assertEquals("Ana Torres", saved.displayName)
        assertEquals("ana@example.com", saved.contact?.value)
    }

    @Test
    fun aSaveFailureKeepsTheValuesAndFlagsTheSnackbar() = runTest {
        val rig = rig(handle = SavedStateHandle(mapOf(RouteArgs.GATE to true)))
        rig.fillValid()
        rig.profiles.failNext = AppError.Offline

        rig.vm.effect.test {
            rig.vm.onEvent(ProfileFormEvent.Submit)
            runCurrent()
            expectNoEvents()
        }
        val state = rig.vm.state.value
        assertTrue(state.saveFailed)
        assertFalse(state.saving)
        assertEquals("Ana Torres", state.name)
        assertEquals("ana@example.com", state.contact)

        rig.vm.onEvent(ProfileFormEvent.SaveErrorShown)
        assertFalse(rig.vm.state.value.saveFailed)
    }

    @Test
    fun theSameFormCanBeRetriedAfterAFailure() = runTest {
        val rig = rig(handle = SavedStateHandle(mapOf(RouteArgs.GATE to true)))
        rig.fillValid()
        rig.profiles.failNext = AppError.Server
        rig.vm.onEvent(ProfileFormEvent.Submit)
        runCurrent()

        rig.vm.effect.test {
            rig.vm.onEvent(ProfileFormEvent.Submit)
            runCurrent()
            assertEquals(ProfileFormEffect.Saved("profile_gate_result"), awaitItem())
        }
    }

    @Test
    fun withNoSessionTheSaveFailsAndSendsNothing() = runTest {
        val rig = rig(signedIn = false)
        rig.fillValid()
        rig.vm.onEvent(ProfileFormEvent.Submit)
        runCurrent()
        assertTrue(rig.vm.state.value.saveFailed)
        assertTrue(rig.profiles.upserts.isEmpty())
    }

    @Test
    fun anExistingProfileFillsTheFormForAnEdit() = runTest {
        val profiles = FakeProfileRepository()
        val at = Instant.parse("2026-11-01T00:00:00Z")
        profiles.seed(Profile(address, "Ana Torres", Contact(ContactType.PHONE, "+51987654321"), at, at))

        val state = rig(profiles = profiles).vm.state.value

        assertEquals("Ana Torres", state.name)
        assertEquals(ContactType.PHONE, state.contactType)
        assertEquals("+51987654321", state.contact)
    }

    @Test
    fun theGateBodyFollowsTheRole() = runTest {
        assertEquals(AppRole.Lender, rig(role = AppRole.Lender).vm.state.value.role)
        assertEquals(AppRole.Borrower, rig(role = AppRole.Borrower).vm.state.value.role)
    }

    @Test
    fun theLegalLinksOpenTheConfiguredPages() = runTest {
        val rig = rig()
        rig.vm.effect.test {
            rig.vm.onEvent(ProfileFormEvent.OpenTerms)
            rig.vm.onEvent(ProfileFormEvent.OpenPrivacy)
            assertEquals(ProfileFormEffect.OpenLink(testConfig().termsUrl), awaitItem())
            assertEquals(ProfileFormEffect.OpenLink(testConfig().privacyUrl), awaitItem())
        }
    }
}
