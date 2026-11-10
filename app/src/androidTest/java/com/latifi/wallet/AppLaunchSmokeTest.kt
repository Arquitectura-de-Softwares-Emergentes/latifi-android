package com.latifi.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boots the real application class and activity with the placeholder project id. The app must
 * not crash and the launch must land on the borrower tabs.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchLandsOnTheBorrowerTabs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = context.getString(R.string.nav_home)
        val loans = context.getString(R.string.nav_loans)
        rule.onNode(hasText(home) and isSelectable()).assertIsDisplayed()
        rule.onNode(hasText(loans) and isSelectable()).assertIsDisplayed()
    }

    @Test
    fun appBootsWithThePlaceholderReownId() {
        assertFalse(rule.activity.config.isWalletConfigured)
        assertFalse(rule.activity.isFinishing)
    }
}
