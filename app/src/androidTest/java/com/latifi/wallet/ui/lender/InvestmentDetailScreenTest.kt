package com.latifi.wallet.ui.lender

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.domain.model.InstallmentState
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.components.SKELETON_CARD_TAG
import com.latifi.wallet.ui.profile.ProfileViewModel
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** S16 on a device: the installments as Received, Due and Upcoming, no primary action, the API status and the states. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class InvestmentDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val clock = Clock.systemUTC()
    private var reloads = 0

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val asOf = Instant.parse("2026-11-10T12:00:00Z")
    private val rates = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = false)), asOf, stale = false),
    )

    private fun lent(status: LoanStatus, paid: Int, fundedDaysAgo: Long = 12): Loan =
        FakeData.loan(
            clock, 40, FakeData.LUCIA, FakeData.DEMO_ADDRESS, 150, 1000, 30, 3, status,
            paid = paid, requestedDaysAgo = fundedDaysAgo + 1, fundedDaysAgo = fundedDaysAgo,
        )

    private fun state(loan: Loan, name: String? = "Lucia Fernandez") = InvestmentDetailUiState(
        content = Loadable.Success(InvestmentDetailViewModel.detailOf(loan)),
        borrowerName = name,
        borrowerShortAddress = ProfileViewModel.shorten(loan.borrower),
        rates = rates,
    )

    private fun show(state: InvestmentDetailUiState, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val screen = @androidx.compose.runtime.Composable {
                    InvestmentDetailScreen(state = state, onReload = { reloads += 1 }, onBack = {})
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun startsWith(prefix: String) = SemanticsMatcher("content description starts with \"$prefix\"") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(prefix) } == true
    }

    @Test
    fun theInstallmentsShowReceivedDueAndUpcomingWithIconsAndText() {
        val loan = lent(LoanStatus.ACTIVE, paid = 1)
        show(state(loan))
        val schedule = (state(loan).content as Loadable.Success).data.schedule
        val words = mapOf(
            InstallmentState.PAID to R.string.installment_state_received,
            InstallmentState.DUE to R.string.installment_state_due,
            InstallmentState.UPCOMING to R.string.installment_state_upcoming,
        )
        schedule.forEach { entry ->
            val spoken = text(
                R.string.installment_row_a11y,
                entry.index,
                schedule.size,
                TokenAmount(entry.amount).format(locale),
                DateFormat.short(entry.dueAt!!, locale, LocalDate.now()),
                text(words.getValue(entry.state)),
            )
            rule.onNodeWithContentDescription(spoken).performScrollTo().assertIsDisplayed()
        }
        assertEquals(listOf(InstallmentState.PAID, InstallmentState.DUE, InstallmentState.UPCOMING), schedule.map { it.state })
        // The word Paid is the borrower's. The lender reads Received.
        rule.onNodeWithText(text(R.string.installment_state_paid)).assertDoesNotExist()
    }

    @Test
    fun theRowsReadAsPhrasesAndThereIsNoPrimaryButton() {
        show(state(lent(LoanStatus.ACTIVE, paid = 1)))

        listOf(
            R.string.detail_rate, R.string.detail_term, R.string.detail_installments, R.string.investment_expected,
            R.string.detail_next_due,
        ).forEach { label -> rule.onNode(startsWith("${text(label)}, ")).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithContentDescription("${text(R.string.detail_borrower)}, Lucia Fernandez, 0x1111...1111")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.fund_cta)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.pay_cta)).assertDoesNotExist()
    }

    @Test
    fun anActiveLoanWithAPastDueDateShowsThePendingChipNotOverdue() {
        show(state(lent(LoanStatus.ACTIVE, paid = 0, fundedDaysAgo = 50)))

        rule.onNodeWithContentDescription(text(R.string.status_pending_a11y)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.status_overdue_a11y)).assertDoesNotExist()
    }

    @Test
    fun aDefaultedLoanShowsTheOverdueChip() {
        show(state(lent(LoanStatus.DEFAULTED, paid = 0, fundedDaysAgo = 50)))

        rule.onNodeWithContentDescription(text(R.string.status_overdue_a11y)).assertIsDisplayed()
    }

    @Test
    fun aMissingLoanShowsTheNotFoundCopyAndReload() {
        show(InvestmentDetailUiState(content = Loadable.Error(ErrorKind.NotFound)))

        rule.onNodeWithText(text(R.string.err_not_found)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        assertEquals(1, reloads)
    }

    @Test
    fun whileLoadingTheScreenShowsASkeleton() {
        show(InvestmentDetailUiState(content = Loadable.Loading))

        rule.onNodeWithContentDescription(text(R.string.loading_generic)).assertExists()
        rule.onAllNodesWithTag(SKELETON_CARD_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun atTwoHundredPercentTextNothingIsTruncated() {
        show(state(lent(LoanStatus.ACTIVE, paid = 1)), fontScale = 2.0f)

        rule.assertNoTruncatedText()
    }
}
