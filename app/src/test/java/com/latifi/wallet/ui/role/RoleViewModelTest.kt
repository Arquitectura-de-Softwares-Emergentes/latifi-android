package com.latifi.wallet.ui.role

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun startsWithNoChoice() {
        assertNull(RoleViewModel(SavedStateHandle(), FakePrefsStore()).state.value.selected)
    }

    @Test
    fun selectingACardChangesTheSelection() {
        val vm = RoleViewModel(SavedStateHandle(), FakePrefsStore())
        vm.onEvent(RoleEvent.Select(AppRole.Lender))
        assertEquals(AppRole.Lender, vm.state.value.selected)
        vm.onEvent(RoleEvent.Select(AppRole.Borrower))
        assertEquals(AppRole.Borrower, vm.state.value.selected)
    }

    @Test
    fun theChoiceSurvivesANewViewModelOnTheSameSavedState() {
        val handle = SavedStateHandle()
        RoleViewModel(handle, FakePrefsStore()).onEvent(RoleEvent.Select(AppRole.Lender))
        assertEquals(AppRole.Lender, RoleViewModel(handle, FakePrefsStore()).state.value.selected)
    }

    @Test
    fun confirmWithNoChoiceDoesNothing() = runTest {
        val prefs = FakePrefsStore()
        val vm = RoleViewModel(SavedStateHandle(), prefs)
        vm.effect.test {
            vm.onEvent(RoleEvent.Confirm)
            runCurrent()
            expectNoEvents()
        }
        assertNull(prefs.role.first())
    }

    @Test
    fun confirmStoresTheRoleAndOpensTheTabs() = runTest {
        val prefs = FakePrefsStore()
        val vm = RoleViewModel(SavedStateHandle(), prefs)
        vm.onEvent(RoleEvent.Select(AppRole.Lender))

        vm.effect.test {
            vm.onEvent(RoleEvent.Confirm)
            runCurrent()
            assertEquals(RoleEffect.GoToMain, awaitItem())
        }
        assertEquals(AppRole.Lender, prefs.role.first())
    }
}
