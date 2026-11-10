package com.latifi.wallet.ui.signin

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SignInSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private val events = mutableListOf<SignInEvent>()

    private val isLiveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    private fun show(state: SignInUiState) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                SignInSheet(state = state, onEvent = { events += it })
            }
        }
    }

    @Test
    fun thePaneTitleIsConfirmItIsYouAndTheReadyStateExplainsTheSignature() {
        show(SignInUiState(SignInPhase.Ready))
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, text(R.string.signin_title))).assertExists()
        rule.onNodeWithText(text(R.string.signin_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.signin_cta)).assertIsEnabled().performClick()
        rule.onNodeWithText(text(R.string.signin_cancel)).performClick()
        assertEquals(listOf(SignInEvent.Confirm, SignInEvent.Cancel), events)
    }

    @Test
    fun aSessionThatEndedUsesTheReauthBody() {
        show(SignInUiState(SignInPhase.Ready, reauth = true))
        rule.onNodeWithText(text(R.string.signin_reauth_body)).assertIsDisplayed()
    }

    @Test
    fun theWaitingStateHasOnePoliteLiveRegion() {
        show(SignInUiState(SignInPhase.Waiting))
        rule.onNodeWithText(text(R.string.connect_waiting)).assertIsDisplayed()
        rule.onAllNodes(isLiveRegion).assertCountEquals(1)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
    }

    @Test
    fun theTimeoutStateOffersKeepWaitingReconnectAndCancel() {
        show(SignInUiState(SignInPhase.TimedOut))
        rule.onNodeWithText(text(R.string.tx_timeout_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.tx_timeout_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.tx_keep_waiting)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.signin_cancel)).assertIsDisplayed().performClick()
        assertEquals(listOf(SignInEvent.Reconnect, SignInEvent.KeepWaiting, SignInEvent.Cancel), events)
        rule.onAllNodes(isLiveRegion).assertCountEquals(1)
    }

    @Test
    fun theRateLimitStateDisablesTheButtonAndShowsTheCountdownInAPoliteRegion() {
        show(SignInUiState(SignInPhase.RateLimited, secondsLeft = 12))
        rule.onNodeWithText(context.getString(R.string.signin_ratelimited, 12)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.signin_cta)).assertIsNotEnabled()
        rule.onAllNodes(isLiveRegion).assertCountEquals(1)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
    }

    @Test
    fun theInvalidStateShowsTheFailureInAnAssertiveRegionAndOffersARetry() {
        show(SignInUiState(SignInPhase.Invalid))
        rule.onNodeWithText(text(R.string.signin_failed)).assertIsDisplayed()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)).assertExists()
        rule.onNodeWithText(text(R.string.signin_cta)).assertIsEnabled()
    }

    @Test
    fun declinedAndSessionLostStatesExplainThemselvesAndKeepCancelAvailable() {
        show(SignInUiState(SignInPhase.SessionLost))
        rule.onNodeWithText(text(R.string.signin_session_lost)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.signin_cancel)).assertIsDisplayed()
    }
}
