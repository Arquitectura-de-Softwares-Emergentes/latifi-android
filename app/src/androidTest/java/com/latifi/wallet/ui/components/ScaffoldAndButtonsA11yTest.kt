package com.latifi.wallet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 48dp targets, roles, wrapping labels, heading order and the safe default focus of dialogs. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ScaffoldAndButtonsA11yTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun threeButtonRolesAreAtLeast48dpHighWithTheButtonRoleAndRespondToTaps() {
        rule.enableAccessibilityChecks()
        val taps = Counter(0)
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                Column(Modifier.padding(16.dp)) {
                    PrimaryButton(text = "Request loan", onClick = { taps.value += 1 })
                    SecondaryButton(text = "Review installments", onClick = { taps.value += 10 })
                    TertiaryButton(text = "Open terms", onClick = { taps.value += 100 })
                }
            }
        }
        for (label in listOf("Request loan", "Review installments", "Open terms")) {
            rule.onNodeWithText(label).assertHeightIsAtLeast(48.dp).assert(isButton).performClick()
        }
        assertEquals(111, taps.value)
    }

    @Test
    fun aLongLabelWrapsAtFontScaleTwoWithoutTruncation() {
        val label = "Confirm the payment of your second installment today"
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                WithFontScale(2.0f) {
                    PrimaryButton(text = label, onClick = {}, modifier = Modifier.width(280.dp))
                }
            }
        }
        val node = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        assertTrue("the label should wrap onto several lines", layout.lineCount > 1)
        assertFalse("the label must not be cut", layout.hasVisualOverflow)
        rule.onNodeWithText(label).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theDisabledReasonShowsInTextUnderTheButton() {
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                PrimaryButton(
                    text = "Start with this role",
                    onClick = {},
                    enabled = false,
                    disabledReason = "Choose a role to start.",
                )
            }
        }
        rule.onNodeWithText("Choose a role to start.").assertIsDisplayed()
    }

    @Test
    fun scaffoldTitleIsAHeadingThatComesFirstInReadingOrder() {
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                ScreenScaffold(title = "Loan details", onBack = {}) { padding ->
                    Column(Modifier.padding(padding)) {}
                }
            }
        }
        val title = rule.onNodeWithText("Loan details")
        title.assert(isHeading())
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, -1f))
    }

    @Test
    fun backArrowHasADescriptionAnd48dpSizeAndGoesBack() {
        var backs = 0
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                ScreenScaffold(title = "Loan details", onBack = { backs++ }) { padding ->
                    Column(Modifier.padding(padding)) {}
                }
            }
        }
        val back = rule.onNodeWithContentDescription(context.getString(R.string.action_back))
        back.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun confirmDialogGivesInitialFocusToTheSafeActionAndExposesItsPaneTitle() {
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                ConfirmDialog(
                    title = "Disconnect wallet?",
                    body = "You will need to connect your wallet again to use LatiFi.",
                    safeLabel = "Keep wallet connected",
                    onSafe = {},
                    confirmLabel = "Disconnect wallet",
                    onConfirm = {},
                    destructive = true,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Keep wallet connected").assertIsFocused()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Disconnect wallet?")).assertExists()
    }

    private class Counter(var value: Int)
}
