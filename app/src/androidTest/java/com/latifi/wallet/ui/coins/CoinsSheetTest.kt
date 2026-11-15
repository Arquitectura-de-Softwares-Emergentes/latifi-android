package com.latifi.wallet.ui.coins

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxPrimary
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.tx.TxStatusSheetContent
import com.latifi.wallet.ui.tx.TxStatusUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S18 on a device: the body with the per-wallet cap, the one primary, and the Confirmed button of the flow. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class CoinsSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    @Test
    fun theSheetShowsTheCapAndOnePrimaryThatConfirms() {
        var confirmed = 0
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                CoinsSheetContent(needsWallet = false, onConfirm = { confirmed += 1 })
            }
        }

        rule.onNodeWithText(text(R.string.coins_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.coins_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.coins_confirm)).assertIsDisplayed().performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun withNoWalletTheSheetSaysTheConnectionEnded() {
        rule.setContent {
            LatiFiTheme(darkTheme = false) { CoinsSheetContent(needsWallet = true, onConfirm = {}) }
        }

        rule.onNodeWithText(text(R.string.tx_fail_session)).assertIsDisplayed()
    }

    @Test
    fun theConfirmedStateOffersViewMyBalance() {
        var viewed = 0
        val flow = TxContext(TxAction.Coins, confirmedPrimary = TxPrimary(R.string.coins_view_balance) { viewed += 1 })
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                TxStatusSheetContent(
                    ui = TxStatusUiState(state = TxState.Confirmed(hash = null, loanId = null), context = flow),
                    onEvent = {},
                )
            }
        }

        rule.onNodeWithText(text(R.string.coins_view_balance)).assertIsDisplayed()
    }

    @Test
    fun theSheetDoesNotTruncateAtTwoHundredPercentText() {
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                WithFontScale(2.0f) { CoinsSheetContent(needsWallet = true, onConfirm = {}) }
            }
        }

        rule.assertNoTruncatedText()
    }
}
