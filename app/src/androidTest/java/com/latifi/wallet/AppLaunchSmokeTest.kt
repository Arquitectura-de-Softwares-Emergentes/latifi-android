package com.latifi.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.core.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boots the real application class and activity with the placeholder project id. A fresh install
 * has no session and has not seen onboarding, so the start destination is the first onboarding
 * page, and the app must not crash.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aFreshInstallOpensTheFirstOnboardingPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.onb1_title)
        // The start destination is read from storage, so give it a moment before asserting.
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun appBootsWithThePlaceholderReownId() {
        assertFalse(rule.activity.config.isWalletConfigured)
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun thePlaceholderIdBootsDemoModeWithThePlainHostAndShowsAScreenWithoutACrash() {
        // The wallet SDK never starts with the placeholder id, so the plain host and the fake wallet run.
        assertEquals(AppMode.Demo, rule.activity.config.mode)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.onb1_title)
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).assertIsDisplayed()
        assertFalse(rule.activity.isFinishing)
    }
}
