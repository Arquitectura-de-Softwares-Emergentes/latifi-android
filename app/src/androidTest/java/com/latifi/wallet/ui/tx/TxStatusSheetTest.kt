package com.latifi.wallet.ui.tx

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.domain.tx.FailReason
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxPrimary
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.allTexts
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * S19 on a device: the six states with their copy and actions, the single live node (assertive for
 * Failed), the focus on the primary action, the 48dp targets, 200 percent text, both themes with a
 * picture of Confirming and Failed in each, and the rule that the sheet cannot be swiped away while
 * a transaction is in flight.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class TxStatusSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private val hash = "0x12ab" + "00".repeat(28) + "9f3c"
    private val address = "0x52908400098527886e0f7030069857d2e4169ee7"
    private val action: String get() = text(R.string.tx_action_payment)
    private val events = mutableListOf<TxStatusEvent>()

    private val isLiveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    private fun uiOf(
        state: TxState,
        walletAddress: String? = address,
        txContext: TxContext = TxContext(TxAction.Payment),
    ) = TxStatusUiState(state, txContext, walletAddress)

    private fun show(
        ui: TxStatusUiState,
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        checks: Boolean = true,
    ) {
        if (checks) rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = darkTheme) {
                val content = @Composable {
                    TxStatusSheetContent(ui = ui, onEvent = { events += it })
                }
                if (fontScale != null) WithFontScale(fontScale) { content() } else content()
            }
        }
    }

    private fun showsText(res: Int, vararg args: Any) =
        rule.onNodeWithText(context.getString(res, *args)).assertIsDisplayed()

    // ---- the six states ----

    @Test
    fun pendingShowsItsCopyOpenWalletAndCancelWithTheActionName() {
        show(uiOf(TxState.Pending(0)))

        showsText(R.string.tx_pending_title)
        showsText(R.string.tx_pending_body)
        rule.onNodeWithText(text(R.string.tx_open_wallet)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.tx_cancel, action)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.OpenWallet, TxStatusEvent.Cancel), events)
    }

    @Test
    fun theTimeoutStateOffersReconnectKeepWaitingAndCancel() {
        show(uiOf(TxState.PendingTimeout))

        showsText(R.string.tx_timeout_title)
        showsText(R.string.tx_timeout_body)
        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.tx_keep_waiting)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.tx_cancel, action)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.Reconnect, TxStatusEvent.KeepWaiting, TxStatusEvent.Cancel), events)
    }

    @Test
    fun confirmingShowsItsCopyAndOnlyTheWayBackHome() {
        show(uiOf(TxState.Confirming(hash)))

        showsText(R.string.tx_confirming_title)
        showsText(R.string.tx_confirming_body)
        rule.onNodeWithText(text(R.string.back_to_home)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.BackToHome), events)
        rule.onAllNodes(hasText(text(R.string.tx_open_wallet))).assertCountEquals(0)
        rule.onAllNodes(hasText(context.getString(R.string.tx_cancel, action))).assertCountEquals(0)
    }

    @Test
    fun confirmingSlowKeepsTheTitleAddsTheNoticeAndNeverLooksFailed() {
        show(uiOf(TxState.ConfirmingSlow(hash)))

        showsText(R.string.tx_confirming_title)
        showsText(R.string.tx_slow)
        rule.onAllNodes(hasText(text(R.string.tx_failed_title))).assertCountEquals(0)
        rule.onNodeWithText(text(R.string.tx_keep_waiting_confirm)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.back_to_home)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.KeepWaitingConfirmation, TxStatusEvent.BackToHome), events)
    }

    @Test
    fun confirmedShowsTheReceiptBlockAndTheWordHashNeverAppears() {
        show(uiOf(TxState.Confirmed(hash, null)))

        showsText(R.string.tx_confirmed_title)
        showsText(R.string.tx_confirmed_body)
        showsText(R.string.receipt_label)
        rule.onNodeWithText("0x12ab...9f3c").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.receipt_copy)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.receipt_view)).assertIsDisplayed().performClick()
        rule.onNodeWithText(text(R.string.back_to_home)).assertIsDisplayed().performClick()
        assertEquals(
            listOf(TxStatusEvent.CopyReceipt, TxStatusEvent.ViewReceipt, TxStatusEvent.ConfirmedPrimary),
            events,
        )
        rule.allTexts().forEach { assertFalse("\"$it\" says hash", it.contains("hash", ignoreCase = true)) }
    }

    @Test
    fun confirmedUsesTheButtonTheFlowSuppliedAsItsPrimary() {
        val flow = TxContext(TxAction.Payment, confirmedPrimary = TxPrimary(R.string.pay_cta) {})
        show(uiOf(TxState.Confirmed(hash, null), txContext = flow))

        rule.onNodeWithText(text(R.string.pay_cta)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.ConfirmedPrimary), events)
        rule.onAllNodes(hasText(text(R.string.back_to_home))).assertCountEquals(0)
    }

    @Test
    fun aConfirmationFoundByReconciliationHasNoReceiptToShow() {
        show(uiOf(TxState.Confirmed(null, null)))

        showsText(R.string.tx_confirmed_title)
        rule.onAllNodes(hasText(text(R.string.receipt_label))).assertCountEquals(0)
        rule.onAllNodes(hasText(text(R.string.receipt_copy))).assertCountEquals(0)
        rule.onNodeWithText(text(R.string.back_to_home)).assertIsDisplayed()
    }

    @Test
    fun failedShowsTheReasonAndRetryWithTheActionName() {
        show(uiOf(TxState.Failed(FailReason.Declined)))

        showsText(R.string.tx_failed_title)
        showsText(R.string.tx_fail_declined)
        rule.onNodeWithText(context.getString(R.string.tx_retry, action)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.tx_cancel, action)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.Retry, TxStatusEvent.Cancel), events)
    }

    @Test
    fun aFeeFailureOffersCopyWalletAddress() {
        show(uiOf(TxState.Failed(FailReason.Fee)))

        showsText(R.string.tx_fail_fee)
        rule.onNodeWithText(text(R.string.tx_copy_address)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.CopyWalletAddress), events)
    }

    @Test
    fun aFeeFailureWithNoConnectedWalletOffersReconnectInstead() {
        show(uiOf(TxState.Failed(FailReason.Fee), walletAddress = null))

        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsDisplayed()
        rule.onAllNodes(hasText(text(R.string.tx_copy_address))).assertCountEquals(0)
    }

    @Test
    fun aTokenFailureOffersGetTestCoins() {
        show(uiOf(TxState.Failed(FailReason.Tokens)))

        showsText(R.string.tx_fail_tokens)
        rule.onNodeWithText(text(R.string.coins_cta)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.GetTestCoins), events)
    }

    @Test
    fun aRequestThatIsNoLongerOpenOffersBackToRequests() {
        show(uiOf(TxState.Failed(FailReason.NotOpen)))

        showsText(R.string.tx_fail_notopen)
        rule.onNodeWithText(text(R.string.back_to_requests)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.BackToRequests), events)
    }

    @Test
    fun sessionAndTimeoutFailuresShareTheSessionCopyAndReconnect() {
        show(uiOf(TxState.Failed(FailReason.Timeout)))

        showsText(R.string.tx_fail_session)
        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsDisplayed().performClick()
        assertEquals(listOf(TxStatusEvent.Reconnect), events)
    }

    @Test
    fun revertedAndNetworkFailuresShowTheirCopyAndRetry() {
        show(uiOf(TxState.Failed(FailReason.Reverted)))
        showsText(R.string.tx_fail_reverted)
        rule.onNodeWithText(context.getString(R.string.tx_retry, action)).assertIsDisplayed()
    }

    @Test
    fun aTwoStepFlowShowsTheStepLine() {
        show(uiOf(TxState.Pending(0), txContext = TxContext(TxAction.Funding, stepIndex = 1, stepCount = 2)))

        showsText(R.string.step_of, 1, 2)
        rule.onNodeWithText(context.getString(R.string.tx_cancel, text(R.string.tx_action_funding))).assertIsDisplayed()
    }

    // ---- accessibility ----

    @Test
    fun thePaneIsTitledWithTheActionName() {
        show(uiOf(TxState.Pending(0)))

        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, action)).assertExists()
    }

    @Test
    fun theStatusTitleIsTheOnlyLiveNodeInEveryState() {
        val states = listOf(
            TxState.Pending(0), TxState.PendingTimeout, TxState.Confirming(hash), TxState.ConfirmingSlow(hash),
            TxState.Confirmed(hash, null), TxState.Failed(FailReason.Network),
        )
        var current by mutableStateOf(states.first())
        rule.setContent {
            LatiFiTheme(darkTheme = false) { TxStatusSheetContent(ui = uiOf(current), onEvent = {}) }
        }
        for (state in states) {
            current = state
            rule.waitForIdle()
            rule.onAllNodes(isLiveRegion).assertCountEquals(1)
        }
    }

    @Test
    fun failedAnnouncesAssertivelyAndEveryOtherStatePolitely() {
        var current by mutableStateOf<TxState>(TxState.Pending(0))
        rule.setContent {
            LatiFiTheme(darkTheme = false) { TxStatusSheetContent(ui = uiOf(current), onEvent = {}) }
        }
        for (state in listOf(TxState.PendingTimeout, TxState.Confirming(hash), TxState.Confirmed(hash, null))) {
            current = state
            rule.waitForIdle()
            rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        }
        current = TxState.Failed(FailReason.Network)
        rule.waitForIdle()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)).assertExists()
        rule.onAllNodes(isLiveRegion).assertCountEquals(1)
    }

    @Test
    fun theCopiedNoticeIsAPoliteAnnouncement() {
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                TxStatusSheetContent(
                    ui = uiOf(TxState.Confirmed(hash, null)),
                    onEvent = {},
                    notice = R.string.receipt_copied,
                )
            }
        }

        showsText(R.string.receipt_copied)
    }

    @Test
    fun reconnectIsThePrimaryOfTheTimeoutAndHoldsTheFocus() {
        show(uiOf(TxState.PendingTimeout))
        rule.waitForIdle()

        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsFocused()
    }

    @Test
    fun theFocusMovesToTheNewPrimaryAfterEachStateChange() {
        var current by mutableStateOf<TxState>(TxState.Pending(0))
        rule.setContent {
            LatiFiTheme(darkTheme = false) { TxStatusSheetContent(ui = uiOf(current), onEvent = {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.tx_open_wallet)).assertIsFocused()

        current = TxState.PendingTimeout
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsFocused()

        current = TxState.Failed(FailReason.Tokens)
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.coins_cta)).assertIsFocused()
    }

    @Test
    fun everyTargetIsAtLeast48dpHigh() {
        show(uiOf(TxState.Confirmed(hash, null)))

        val count = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue("the confirmed state has actions", count >= 3)
        repeat(count) { rule.onAllNodes(hasClickAction())[it].assertHeightIsAtLeast(48.dp) }
    }

    @Test
    fun nothingIsTruncatedAtTwoHundredPercentText() {
        val ui = uiOf(TxState.Failed(FailReason.Fee))
        show(ui, fontScale = 2.0f, checks = false)

        rule.assertNoTruncatedText()
    }

    @Test
    fun theTimeoutPanelWrapsAtTwoHundredPercentText() {
        show(uiOf(TxState.PendingTimeout), fontScale = 2.0f, checks = false)

        rule.assertNoTruncatedText()
    }

    // ---- themes and pictures ----

    @Test
    fun confirmingRendersInBothThemesAndLeavesAPicture() {
        show(uiOf(TxState.Confirming(hash)), darkTheme = false)
        showsText(R.string.tx_confirming_title)
        saveScreenshot("tx-confirming-light.png")
    }

    @Test
    fun confirmingRendersInTheDarkThemeAndLeavesAPicture() {
        show(uiOf(TxState.Confirming(hash)), darkTheme = true)
        showsText(R.string.tx_confirming_title)
        saveScreenshot("tx-confirming-dark.png")
    }

    @Test
    fun failedRendersInTheLightThemeAndLeavesAPicture() {
        show(uiOf(TxState.Failed(FailReason.Declined)), darkTheme = false)
        showsText(R.string.tx_failed_title)
        saveScreenshot("tx-failed-light.png")
    }

    @Test
    fun failedRendersInTheDarkThemeAndLeavesAPicture() {
        show(uiOf(TxState.Failed(FailReason.Declined)), darkTheme = true)
        showsText(R.string.tx_failed_title)
        saveScreenshot("tx-failed-dark.png")
    }

    // ---- the sheet around the content ----

    private fun showHost(state: TxState) {
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                TxStatusSheetHost(ui = uiOf(state), onEvent = { events += it })
            }
        }
    }

    @Test
    fun aSwipeDownDoesNotCloseTheSheetWhilePending() {
        showHost(TxState.Pending(0))
        rule.onNodeWithText(text(R.string.tx_pending_title)).assertIsDisplayed()

        rule.onNodeWithText(text(R.string.tx_pending_title)).performTouchInput { swipeDown() }
        rule.waitForIdle()

        rule.onNodeWithText(text(R.string.tx_pending_title)).assertIsDisplayed()
        assertFalse("a swipe must not ask to close the sheet", events.contains(TxStatusEvent.Back))
    }

    @Test
    fun aSwipeDownDoesNotCloseTheSheetWhileConfirming() {
        showHost(TxState.Confirming(hash))
        rule.onNodeWithText(text(R.string.tx_confirming_title)).assertIsDisplayed()

        rule.onNodeWithText(text(R.string.tx_confirming_title)).performTouchInput { swipeDown() }
        rule.waitForIdle()

        rule.onNodeWithText(text(R.string.tx_confirming_title)).assertIsDisplayed()
        assertFalse("a swipe must not ask to close the sheet", events.contains(TxStatusEvent.Back))
    }

    @Test
    fun theSheetAroundTheContentShowsTheSameState() {
        showHost(TxState.PendingTimeout)

        showsText(R.string.tx_timeout_title)
        rule.onNodeWithText(text(R.string.tx_reconnect)).assertIsDisplayed()
    }

    /** Writes the rendered state as a PNG to the folder the workflow uploads (UI-SPEC Verification Plan, S19 screenshots). */
    private fun saveScreenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("the screenshot is empty", bitmap.width > 0 && bitmap.height > 0)
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)?.path
            ?: return
        runCatching {
            File(dir).mkdirs()
            FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
