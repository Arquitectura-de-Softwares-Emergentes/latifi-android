package com.latifi.wallet.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.testConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(
        prefs: FakePrefsStore = FakePrefsStore(),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = OnboardingViewModel(handle, prefs, testConfig())

    @Test
    fun startsOnTheFirstPageWithTheConnectStep() {
        val state = viewModel().state.value
        assertEquals(0, state.page)
        assertTrue(state.isFirst)
        assertTrue(state.showConnect)
    }

    @Test
    fun nextAndBackMoveThroughTheFourPages() {
        val vm = viewModel()
        repeat(3) { vm.onEvent(OnboardingEvent.Next) }
        assertEquals(3, vm.state.value.page)
        assertTrue(vm.state.value.isLast)

        vm.onEvent(OnboardingEvent.Back)
        assertEquals(2, vm.state.value.page)
    }

    @Test
    fun backOnTheFirstPageStaysThere() {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.Back)
        assertEquals(0, vm.state.value.page)
    }

    @Test
    fun aSwipeSettlingOnAPageUpdatesTheState() {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.PageSettled(2))
        assertEquals(2, vm.state.value.page)
        vm.onEvent(OnboardingEvent.PageSettled(99))
        assertEquals(3, vm.state.value.page)
    }

    @Test
    fun thePageSurvivesANewViewModelOnTheSameSavedState() {
        val handle = SavedStateHandle()
        viewModel(handle = handle).onEvent(OnboardingEvent.Next)

        assertEquals(1, viewModel(handle = handle).state.value.page)
    }

    @Test
    fun finishingTheFirstRunMarksOnboardingSeenAndGoesToConnect() = runTest {
        val prefs = FakePrefsStore()
        val vm = viewModel(prefs)
        repeat(3) { vm.onEvent(OnboardingEvent.Next) }

        vm.effect.test {
            vm.onEvent(OnboardingEvent.Next)
            runCurrent()
            assertEquals(OnboardingEffect.GoToConnect, awaitItem())
        }
        assertTrue(prefs.onboardingSeen.first())
    }

    @Test
    fun reopenedFromProfileTheLastPageLeavesWithoutTouchingTheFlag() = runTest {
        val prefs = FakePrefsStore()
        val vm = viewModel(prefs, SavedStateHandle(mapOf(OnboardingViewModel.KEY_SHOW_CONNECT to false)))
        assertFalse(vm.state.value.showConnect)
        repeat(3) { vm.onEvent(OnboardingEvent.Next) }

        vm.effect.test {
            vm.onEvent(OnboardingEvent.Next)
            runCurrent()
            assertEquals(OnboardingEffect.Leave, awaitItem())
        }
        assertFalse(prefs.onboardingSeen.first())
    }

    @Test
    fun theLegalLinksComeFromTheConfig() = runTest {
        val vm = viewModel()
        vm.effect.test {
            vm.onEvent(OnboardingEvent.OpenTerms)
            vm.onEvent(OnboardingEvent.OpenPrivacy)
            assertEquals(OnboardingEffect.OpenLink(testConfig().termsUrl), awaitItem())
            assertEquals(OnboardingEffect.OpenLink(testConfig().privacyUrl), awaitItem())
        }
    }
}
