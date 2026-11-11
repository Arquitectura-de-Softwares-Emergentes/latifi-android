package com.latifi.wallet.ui.pay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.fund.FundData
import com.latifi.wallet.ui.fund.FundScreen
import com.latifi.wallet.ui.fund.FundUiState
import com.latifi.wallet.ui.lender.RequestDetailViewModel
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.tx.TxStatusSheetContent
import com.latifi.wallet.ui.tx.TxStatusUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.time.Clock
import java.time.ZoneOffset

/** S10 and S14 on a device: the rows, the single primary, the late note, the blocked state and the step line. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PayFundScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)

    private fun text(res: Int) = context.getString(res)

    private val active = FakeData.loan(
        clock, 31, FakeData.DEMO_ADDRESS, FakeData.LENDER_A, 100, 1000, 30, 2, LoanStatus.ACTIVE,
        paid = 0, requestedDaysAgo = 40, fundedDaysAgo = 20,
    )

    private fun payData(late: Boolean = false, blocked: Boolean = false) = PayData(
        loan = active,
        index = 1,
        dueAt = FakeData.EPOCH.minusSeconds(5 * 86_400),
        unpaid = TokenAmount(BigInteger.valueOf(50_410_958)),
        allowance = BigInteger.ZERO,
        late = late,
        blocked = blocked,
    )

    private fun showPay(state: PayUiState, onConfirm: () -> Unit = {}, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val screen = @androidx.compose.runtime.Composable {
                    PayScreen(state = state, onConfirm = onConfirm, onReload = {}, onBack = {})
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun showFund(state: FundUiState, onConfirm: () -> Unit = {}, onBackToRequests: () -> Unit = {}, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val screen = @androidx.compose.runtime.Composable {
                    FundScreen(state = state, onConfirm = onConfirm, onBackToRequests = onBackToRequests, onReload = {}, onBack = {})
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun fundState(notOpen: Boolean = false, own: Boolean = false): FundUiState {
        val open = active.copy(status = LoanStatus.OPEN, lender = null, totalDue = BigInteger.ZERO)
        return FundUiState(
            content = Loadable.Success(FundData(RequestDetailViewModel.detailOf(open), BigInteger.ZERO)),
            notOpen = notOpen,
            ownRequest = own,
        )
    }

    @Test
    fun payShowsTheAmountTheLineAndOneConfirmPrimary() {
        var confirmed = 0
        showPay(PayUiState(content = Loadable.Success(payData())), onConfirm = { confirmed += 1 })

        rule.onNodeWithText(text(R.string.pay_line)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.pay_amount)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.network_fee_note)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.pay_confirm)).assertIsDisplayed().performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun aLateInstallmentShowsTheLateNote() {
        showPay(PayUiState(content = Loadable.Success(payData(late = true))))

        rule.onNodeWithText(text(R.string.pay_late)).assertIsDisplayed()
    }

    @Test
    fun anInstallmentThatIsNotLateHasNoLateNote() {
        showPay(PayUiState(content = Loadable.Success(payData(late = false))))

        rule.onNodeWithText(text(R.string.pay_late)).assertDoesNotExist()
    }

    @Test
    fun aBlockedLoanShowsTheBlockedCopyAndNoPrimary() {
        showPay(PayUiState(content = Loadable.Success(payData(blocked = true))))

        rule.onNodeWithText(text(R.string.pay_blocked)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.pay_confirm)).assertDoesNotExist()
    }

    @Test
    fun fundShowsTheSummaryTheTermsNoteAndOneConfirmPrimary() {
        var confirmed = 0
        showFund(fundState(), onConfirm = { confirmed += 1 })

        rule.onNodeWithText(text(R.string.detail_earn), substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.fund_terms_fixed)).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText(text(R.string.fund_confirm)) and hasClickAction()).assertIsDisplayed().performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun aNotOpenRequestShowsTheFailureAndBackToRequests() {
        var back = 0
        showFund(fundState(notOpen = true), onBackToRequests = { back += 1 })

        rule.onNodeWithText(text(R.string.tx_fail_notopen)).assertIsDisplayed()
        rule.onNode(hasText(text(R.string.fund_confirm)) and hasClickAction()).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.back_to_requests)).performClick()

        assertEquals(1, back)
    }

    @Test
    fun theApproveStepShowsTheStepLineAndTheExactAmount() {
        val flow = TxContext(TxAction.Payment, stepIndex = 1, stepCount = 2, allowAmountText = "50.410958")
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                TxStatusSheetContent(ui = TxStatusUiState(state = TxState.Pending(0L), context = flow), onEvent = {})
            }
        }

        rule.onNodeWithText(context.getString(R.string.step_of, 1, 2)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.tx_allow_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.tx_allow_body, "50.410958")).assertIsDisplayed()
    }

    @Test
    fun theSuccessCopyShowsAboveTheReceipt() {
        val flow = TxContext(
            TxAction.Payment,
            successTitleRes = R.string.pay_success_title,
            successBodyRes = R.string.pay_success_body,
        )
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                TxStatusSheetContent(
                    ui = TxStatusUiState(state = TxState.Confirmed(hash = null, loanId = null), context = flow),
                    onEvent = {},
                )
            }
        }

        rule.onNodeWithText(text(R.string.pay_success_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.pay_success_body)).assertIsDisplayed()
    }

    @Test
    fun payAndFundDoNotTruncateAtTwoHundredPercentText() {
        showPay(PayUiState(content = Loadable.Success(payData(late = true))), fontScale = 2.0f)
        rule.assertNoTruncatedText()
    }
}
