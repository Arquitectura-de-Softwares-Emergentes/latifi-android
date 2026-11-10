package com.latifi.wallet.ui.connect

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ConnectScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private val events = mutableListOf<ConnectEvent>()

    private fun show(state: ConnectUiState) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                ConnectScreen(state = state, onEvent = { events += it })
            }
        }
    }

    @Test
    fun idleShowsBothButtonsAndTheConsentLinks() {
        show(ConnectUiState(ConnectPhase.Idle))
        rule.onNodeWithText(text(R.string.connect_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_cta)).assertIsDisplayed().assertIsEnabled().performClick()
        rule.onNodeWithText(text(R.string.connect_nowallet)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.terms_link)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.privacy_link)).assertIsDisplayed().performClick()
        assertEquals(
            listOf(ConnectEvent.Connect, ConnectEvent.NoWallet, ConnectEvent.OpenTerms, ConnectEvent.OpenPrivacy),
            events,
        )
    }

    @Test
    fun aReturningPersonSeesTheReconnectTitle() {
        show(ConnectUiState(ConnectPhase.Idle, returning = true))
        rule.onNodeWithText(text(R.string.connect_title_return)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_title)).assertDoesNotExist()
    }

    @Test
    fun theFailurePanelShowsBothButtonsAndNoPrimaryConnect() {
        show(ConnectUiState(ConnectPhase.Failed))
        rule.onNodeWithText(text(R.string.connect_error_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_error_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_retry)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_nowallet)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_cta)).assertDoesNotExist()
    }

    @Test
    fun notConfiguredDisablesThePrimaryAndShowsItsReason() {
        show(ConnectUiState(ConnectPhase.NotConfigured))
        rule.onNodeWithText(text(R.string.connect_cta)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.wallet_not_configured)).assertIsDisplayed()
    }

    @Test
    fun theWaitingStateExposesAPoliteStatusNodeAndLocksThePrimary() {
        show(ConnectUiState(ConnectPhase.Waiting))
        rule.onNodeWithText(text(R.string.connect_waiting)).assertIsDisplayed()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        rule.onNodeWithText(text(R.string.connect_cta)).assertIsNotEnabled()
    }

    @Test
    fun theFocusOrderIsTitleBodyButtonsConsentNotice() {
        show(ConnectUiState(ConnectPhase.Idle))
        val consent = context.getString(R.string.connect_consent, text(R.string.terms_link), text(R.string.privacy_link))
        val tops = listOf(
            text(R.string.connect_title),
            text(R.string.connect_body),
            text(R.string.connect_cta),
            text(R.string.connect_nowallet),
            consent,
            text(R.string.test_money_notice),
        ).map { rule.onNodeWithText(it).getBoundsInRoot().top.value }
        assertTrue("reading order by position: $tops", tops == tops.sorted() && tops.distinct().size == tops.size)
    }

    @Test
    fun theHelpSheetContentExplainsWalletsAndClosesFromItsButton() {
        rule.enableAccessibilityChecks()
        var closed = 0
        rule.setContent {
            LatiFiTheme(darkTheme = false) { WalletHelpContent(onClose = { closed++ }) }
        }
        rule.onNodeWithText(text(R.string.connect_help_body)).assertIsDisplayed()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, text(R.string.connect_help_title))).assertExists()
        rule.onNodeWithText(text(R.string.connect_help_close)).performClick()
        assertEquals(1, closed)
    }
}
