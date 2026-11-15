package com.latifi.wallet.ui.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.FieldError
import com.latifi.wallet.core.LoanRequestCalculator
import com.latifi.wallet.core.LoanRequestResult
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.RequestField
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * S09 on a device: the stepper and its live value, the shortcut chips, the disabled primary with
 * its visible reason, the error text with the count, the Leave dialog and 200 percent text.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class RequestLoanScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun review(amount: String = "100", installments: Int = 2) =
        (LoanRequestCalculator.calculate(amount, "10.00", 30, installments) as LoanRequestResult.Valid).preview

    private fun show(initial: RequestLoanUiState, actions: (update: ((RequestLoanUiState) -> RequestLoanUiState) -> Unit) -> RequestLoanActions, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            var state by remember { mutableStateOf(initial) }
            LatiFiTheme(darkTheme = false) {
                val content = @Composable {
                    RequestLoanScreen(state = state, actions = actions { change -> state = change(state) })
                }
                if (fontScale != null) WithFontScale(fontScale) { content() } else content()
            }
        }
    }

    @Test
    fun theStepperIsSpokenAndItsValueIsAPoliteLiveRegion() {
        show(
            RequestLoanUiState(),
            { update ->
                RequestLoanActions(
                    onIncrease = { update { it.copy(installments = (it.installments + 1).coerceAtMost(6)) } },
                    onDecrease = { update { it.copy(installments = (it.installments - 1).coerceAtLeast(1)) } },
                )
            },
        )

        rule.onNodeWithContentDescription(text(R.string.request_installments_dec)).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithContentDescription(text(R.string.request_installments_inc)).performScrollTo().performClick()

        rule.onNode(polite and androidx.compose.ui.test.hasText("2 installments")).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.request_installments_dec)).assertIsEnabled()
    }

    @Test
    fun eachShortcutChipSetsItsAmount() {
        val chosen = mutableListOf<Int>()
        show(RequestLoanUiState(), { RequestLoanActions(onAmountChip = { chosen += it }) })
        val group = text(R.string.request_amount)

        listOf(50, 100, 250, 500).forEach { tokens ->
            rule.onNodeWithContentDescription(context.getString(R.string.chip_not_selected_a11y, group, tokens.toString()))
                .performScrollTo().performClick()
        }

        assertEquals(listOf(50, 100, 250, 500), chosen)
    }

    @Test
    fun reviewIsDisabledAndTheReasonIsVisibleUntilTheFormIsValid() {
        show(RequestLoanUiState(), { RequestLoanActions() })

        rule.onNodeWithText(text(R.string.request_review)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.request_disabled_reason)).assertIsDisplayed()
    }

    @Test
    fun aValidFormEnablesReview() {
        show(RequestLoanUiState(amount = "100", term = TermChoice.Days30, preview = review()), { RequestLoanActions() })

        rule.onNodeWithText(text(R.string.request_review)).assertIsEnabled()
    }

    @Test
    fun fieldErrorsShowAsTextWithTheCount() {
        show(
            RequestLoanUiState(
                amount = "10001",
                errors = mapOf(RequestField.AMOUNT to FieldError.OUT_OF_RANGE, RequestField.TERM to FieldError.EMPTY),
                submitErrorCount = 2,
            ),
            { RequestLoanActions() },
        )

        // The count sits at the top, so it is checked before the scrolls below move it out of view.
        rule.onNodeWithText("2 fields need your attention.").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.err_amount_range)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.err_term_range)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLeaveDialogOffersTheSafeActionAndTheConfirmAction() {
        var stayed = 0
        show(
            RequestLoanUiState(amount = "50", leaveDialog = true),
            { RequestLoanActions(onStayEditing = { stayed += 1 }) },
        )

        rule.onNodeWithText(text(R.string.leave_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.leave_confirm)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.leave_stay)).assertIsDisplayed().performClick()

        assertEquals(1, stayed)
    }

    @Test
    fun stepTwoShowsEachInstallmentAndThePublishPrimary() {
        show(
            RequestLoanUiState(step = 2, amount = "100", term = TermChoice.Days30, installments = 2, preview = review()),
            { RequestLoanActions() },
        )

        rule.onNodeWithText(text(R.string.request_review_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.request_publish)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.request_first_due)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.network_fee_note)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aTwelveDigitLocalValueDoesNotTruncateAtTwoHundredPercentText() {
        val huge = LocalValue.Value(BigDecimal("123456789012.50"), LocalCurrency.PEN, stale = false, asOf = Instant.EPOCH)
        show(
            RequestLoanUiState(
                step = 2, amount = "10000", term = TermChoice.Days30, installments = 3,
                preview = review("10000", 3), totalLocal = huge,
            ),
            { RequestLoanActions() },
            fontScale = 2.0f,
        )

        rule.assertNoTruncatedText()
    }

    @Test
    fun stepOneDoesNotTruncateAtTwoHundredPercentText() {
        show(RequestLoanUiState(amount = "100", term = TermChoice.Other, otherDays = "90"), { RequestLoanActions() }, fontScale = 2.0f)

        rule.assertNoTruncatedText()
    }
}
