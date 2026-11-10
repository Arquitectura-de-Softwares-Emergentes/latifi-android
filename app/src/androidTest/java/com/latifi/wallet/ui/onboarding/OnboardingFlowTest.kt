package com.latifi.wallet.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.allTexts
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless pager screen, driven by a small host that does what the view model does. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class OnboardingFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private val opened = mutableListOf<OnboardingEvent>()

    private fun show(fontScale: Float = 1f, showConnect: Boolean = true) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                WithFontScale(fontScale) { Host(showConnect) }
            }
        }
    }

    @Composable
    private fun Host(showConnect: Boolean) {
        var state by remember { mutableStateOf(OnboardingUiState(showConnect = showConnect)) }
        OnboardingScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    OnboardingEvent.Next -> state = state.copy(page = (state.page + 1).coerceAtMost(ONBOARDING_PAGE_COUNT - 1))
                    OnboardingEvent.Back -> state = state.copy(page = (state.page - 1).coerceAtLeast(0))
                    is OnboardingEvent.PageSettled -> state = state.copy(page = event.page)
                    else -> opened += event
                }
            },
        )
    }

    private fun next() {
        rule.onNodeWithText(text(R.string.action_next)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun pageOneShowsTheTitleBodyAndWalletNoteWithNoBackButton() {
        show()
        rule.onNodeWithText(text(R.string.onb1_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.onb1_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.onb1_wallet_note)).assertIsDisplayed()
        rule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.action_back)).assertDoesNotExist()
    }

    @Test
    fun nextMovesToPageTwoAndTheTitleTakesFocus() {
        show()
        next()
        rule.onNodeWithText(text(R.string.onb2_title)).assertIsDisplayed().assertIsFocused()
        rule.onNodeWithText("Step 2 of 4").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.action_back)).assertIsDisplayed()
    }

    @Test
    fun backReturnsToThePreviousPage() {
        show()
        next()
        rule.onNodeWithText(text(R.string.action_back)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.onb1_title)).assertIsDisplayed()
        rule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
    }

    @Test
    fun pageFourShowsConnectTheNoCustodyNoteAndBothLinksAtLeast48dpHigh() {
        show()
        repeat(3) { next() }
        rule.onNodeWithText(text(R.string.onb4_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_cta)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.onb_nocustody)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.terms_link)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText(text(R.string.privacy_link)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theLinksReportTheirEvents() {
        show()
        repeat(3) { next() }
        rule.onNodeWithText(text(R.string.terms_link)).performClick()
        rule.onNodeWithText(text(R.string.privacy_link)).performClick()
        assertEquals(listOf(OnboardingEvent.OpenTerms, OnboardingEvent.OpenPrivacy), opened)
    }

    @Test
    fun reopenedFromProfileTheLastPageOffersNoConnectStep() {
        show(showConnect = false)
        repeat(3) { next() }
        rule.onNodeWithText(text(R.string.onb_finish_help)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.connect_cta)).assertDoesNotExist()
    }

    @Test
    fun noPageEverUsesTheWordBlockchain() {
        show()
        repeat(ONBOARDING_PAGE_COUNT) { page ->
            val texts = rule.allTexts()
            assertFalse("page ${page + 1}: $texts", texts.any { it.contains("blockchain", ignoreCase = true) })
            if (page < ONBOARDING_PAGE_COUNT - 1) next()
        }
    }

    @Test
    fun atFontScaleTwoNoTextIsTruncatedOnTheFirstAndLastPage() {
        show(fontScale = 2.0f)
        rule.assertNoTruncatedText()
        repeat(3) { next() }
        rule.assertNoTruncatedText()
        rule.onNodeWithText(text(R.string.connect_cta)).assertHeightIsAtLeast(48.dp)
    }
}
