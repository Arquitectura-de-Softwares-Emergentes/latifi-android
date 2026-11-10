package com.latifi.wallet.ui.loans

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
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
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.components.SKELETON_CARD_TAG
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** S08 on a device: rows as phrases, the installments, the primary action per status and the states. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class LoanDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val paid = mutableListOf<BigInteger>()
    private var reloads = 0

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val asOf = Instant.parse("2026-11-10T12:00:00Z")
    private val rates = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = false)), asOf, stale = false),
    )
    private val loans = FakeData.loans(Clock.systemUTC())

    private fun detailState(id: Long, lenderName: String? = null) = loans.first { it.loanId == BigInteger.valueOf(id) }.let { loan ->
        LoanDetailUiState(
            content = Loadable.Success(LoanDetailViewModel.detailOf(loan)),
            lenderName = lenderName,
            lenderShortAddress = loan.lender?.let { com.latifi.wallet.ui.profile.ProfileViewModel.shorten(it) },
            rates = rates,
        )
    }

    private fun show(state: LoanDetailUiState, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val screen = @androidx.compose.runtime.Composable {
                    LoanDetailScreen(state = state, onReload = { reloads += 1 }, onBack = {}, onPay = { paid += it })
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun startsWith(prefix: String) = SemanticsMatcher("content description starts with \"$prefix\"") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(prefix) } == true
    }

    @Test
    fun eachDetailRowReadsAsOnePhrase() {
        show(detailState(11))

        rule.onNodeWithContentDescription("${text(R.string.detail_rate)}, ${text(R.string.rate_per_year, "10")}")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("${text(R.string.detail_term)}, ${context.resources.getQuantityString(R.plurals.term_days, 30, 30)}")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(
            "${text(R.string.detail_installments)}, ${context.resources.getQuantityString(R.plurals.installments_count, 3, 3)}",
        ).performScrollTo().assertIsDisplayed()
        rule.onNode(startsWith("${text(R.string.detail_total)}, ")).performScrollTo().assertIsDisplayed()
        rule.onNode(startsWith("${text(R.string.detail_installment_amount)}, ")).performScrollTo().assertIsDisplayed()
        rule.onNode(startsWith("${text(R.string.detail_funded_on)}, ")).performScrollTo().assertIsDisplayed()
        rule.onNode(startsWith("${text(R.string.detail_next_due)}, ")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("${text(R.string.detail_lender)}, 0xaaaa...aaaa").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aKnownLenderShowsThePublicNameInsteadOfTheAddress() {
        show(detailState(11, lenderName = "Rosa Medina"))

        rule.onNodeWithContentDescription("${text(R.string.detail_lender)}, Rosa Medina").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theInstallmentsShowPaidDueAndUpcomingWithTheirSpokenRows() {
        val state = detailState(11)
        show(state)
        val today = LocalDate.now()
        val schedule = (state.content as Loadable.Success).data.schedule
        val words = mapOf(
            InstallmentState.PAID to R.string.installment_state_paid,
            InstallmentState.DUE to R.string.installment_state_due,
            InstallmentState.UPCOMING to R.string.installment_state_upcoming,
        )
        schedule.forEach { entry ->
            val spoken = text(
                R.string.installment_row_a11y,
                entry.index,
                schedule.size,
                TokenAmount(entry.amount).format(locale),
                DateFormat.short(entry.dueAt!!, locale, today),
                text(words.getValue(entry.state)),
            )
            rule.onNodeWithContentDescription(spoken).performScrollTo().assertIsDisplayed()
        }
        assertEquals(listOf(InstallmentState.PAID, InstallmentState.DUE, InstallmentState.UPCOMING), schedule.map { it.state })
    }

    @Test
    fun anActiveLoanOffersPayInstallmentAndTheTapReportsTheLoan() {
        show(detailState(11))

        rule.onNodeWithText(text(R.string.pay_cta)).assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(listOf(BigInteger.valueOf(11)), paid)
    }

    @Test
    fun anOpenRequestHasNoPrimaryAndSaysItIsWaitingForALender() {
        show(detailState(12))

        rule.onNodeWithText(text(R.string.pay_cta)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.loan_waiting_lender)).assertIsDisplayed()
        // The label of the Installments detail row also reads "Installments", so only the heading counts.
        rule.onNode(hasText(text(R.string.installments_list_title)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertDoesNotExist()
    }

    @Test
    fun aRepaidLoanShowsTheSuccessNoteAndNoPrimary() {
        show(detailState(7))

        rule.onNodeWithText(text(R.string.loan_repaid_note)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.pay_cta)).assertDoesNotExist()
    }

    @Test
    fun anOverdueLoanShowsADisabledPrimaryWithTheVisibleReason() {
        show(detailState(5))

        rule.onNodeWithText(text(R.string.pay_cta)).assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.pay_blocked)).assertIsDisplayed()
        assertEquals(emptyList<BigInteger>(), paid)
    }

    @Test
    fun theStatusChipAndTheAttributionRowArePresent() {
        show(detailState(11))

        rule.onNodeWithContentDescription(text(R.string.status_pending_a11y)).assertIsDisplayed()
        rule.onNode(hasText("Rates By Exchange Rate API")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingLoanShowsTheNotFoundCopyAndReload() {
        show(LoanDetailUiState(content = Loadable.Error(ErrorKind.NotFound)))

        rule.onNodeWithText(text(R.string.err_not_found)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        assertEquals(1, reloads)
    }

    @Test
    fun whileLoadingTheScreenShowsASkeleton() {
        show(LoanDetailUiState(content = Loadable.Loading))

        rule.onNodeWithContentDescription(text(R.string.loading_generic)).assertExists()
        rule.onAllNodesWithTag(SKELETON_CARD_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun atTwoHundredPercentTextNothingIsTruncated() {
        show(detailState(11), fontScale = 2.0f)

        rule.onNodeWithText(text(R.string.loan_details_title)).assertIsDisplayed()
        rule.assertNoTruncatedText()
    }
}
