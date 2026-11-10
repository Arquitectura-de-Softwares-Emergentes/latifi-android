package com.latifi.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.latifi.wallet.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Composes a small content under each theme and checks the rendered colors and the app name. */
@RunWith(AndroidJUnit4::class)
class ThemeSmokeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lightThemeShowsTheAppNameAndUsesTheNavyPrimary() {
        var primary = Color.Unspecified
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                Capture { primary = it }
                AppNameText()
            }
        }
        rule.onNodeWithText("LatiFi Wallet").assertIsDisplayed()
        assertEquals(Color(ColorTokens.Light.primary), primary)
    }

    @Test
    fun darkThemeShowsTheAppNameAndUsesTheLimePrimary() {
        var primary = Color.Unspecified
        rule.setContent {
            LatiFiTheme(darkTheme = true) {
                Capture { primary = it }
                AppNameText()
            }
        }
        rule.onNodeWithText("LatiFi Wallet").assertIsDisplayed()
        assertEquals(Color(ColorTokens.Dark.primary), primary)
    }

    @Composable
    private fun AppNameText() {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

    @Composable
    private fun Capture(onPrimary: (Color) -> Unit) {
        val value = MaterialTheme.colorScheme.primary
        SideEffect { onPrimary(value) }
    }
}
