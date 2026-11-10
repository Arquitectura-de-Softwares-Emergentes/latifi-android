package com.latifi.wallet.ui.role

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class RoleScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private val confirmed = mutableListOf<Unit>()

    private fun show() {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                var state by remember { mutableStateOf(RoleUiState()) }
                RoleScreen(
                    state = state,
                    onEvent = { event ->
                        when (event) {
                            is RoleEvent.Select -> state = RoleUiState(event.role)
                            RoleEvent.Confirm -> confirmed += Unit
                        }
                    },
                )
            }
        }
    }

    @Test
    fun theCardsAreRadioItemsThatStartNotSelected() {
        show()
        rule.onNodeWithText(text(R.string.role_borrow)).assert(isRadio).assertIsNotSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText(text(R.string.role_lend)).assert(isRadio).assertIsNotSelected().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun choosingACardSelectsItAndLeavesTheOtherUnselected() {
        show()
        rule.onNodeWithText(text(R.string.role_lend)).performClick()
        rule.onNodeWithText(text(R.string.role_lend)).assertIsSelected()
        rule.onNodeWithText(text(R.string.role_borrow)).assertIsNotSelected()
        rule.onNodeWithText(text(R.string.role_borrow)).performClick()
        rule.onNodeWithText(text(R.string.role_borrow)).assertIsSelected()
        rule.onNodeWithText(text(R.string.role_lend)).assertIsNotSelected()
    }

    @Test
    fun thePrimaryIsOffWithAVisibleReasonUntilARoleIsChosen() {
        show()
        rule.onNodeWithText(text(R.string.role_cta)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.role_cta_disabled)).assertIsDisplayed()

        rule.onNodeWithText(text(R.string.role_borrow)).performClick()

        rule.onNodeWithText(text(R.string.role_cta)).assertIsEnabled().performClick()
        rule.onNodeWithText(text(R.string.role_cta_disabled)).assertDoesNotExist()
        assertEquals(1, confirmed.size)
    }

    @Test
    fun theChangeHintIsVisible() {
        show()
        rule.onNodeWithText(text(R.string.role_change_hint)).assertIsDisplayed()
    }
}
