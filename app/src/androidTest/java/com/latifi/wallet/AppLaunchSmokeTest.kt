package com.latifi.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boots the real application class and activity with the placeholder project id.
 * The app must not crash and must say that the wallet is not configured.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appNameIsDisplayed() {
        rule.onNodeWithText("LatiFi Wallet").assertIsDisplayed()
    }

    @Test
    fun walletNotConfiguredLineIsDisplayedForThePlaceholderId() {
        rule.onNodeWithText("Wallet connection is not set up in this build.").assertIsDisplayed()
    }
}
