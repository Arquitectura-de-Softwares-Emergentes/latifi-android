package com.latifi.wallet.ui.lender

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeReputationRepository
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.theme.LatiFiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant

/** S13 on a device: the spoken chip and rows, the history and its empty state, one primary and the states of Fund request. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class RequestDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = Clock.systemUTC()
    private var funded = 0
    private var reloads = 0
    private var historyReloads = 0

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val asOf = Instant.parse("2026-11-10T12:00:00Z")
    private val rates = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = false)), asOf, stale = false),
    )

    /** A request at reputation 620, so the chip reads Trusted. */
    private val openLoan = FakeData.loan(clock, 21, FakeData.LUCIA, null, 250, 1000, 30, 3, LoanStatus.OPEN, requestedDaysAgo = 1, score = 620)
    private val events = runBlocking { FakeReputationRepository(clock).history(FakeData.LUCIA, null, 0, 5).getOrThrow().items }

    private fun state(
        loan: com.latifi.wallet.domain.model.Loan = openLoan,
        history: RecentHistory = RecentHistory.Items(events),
        own: String? = FakeData.DEMO_ADDRESS,
        name: String? = "Lucia Fernandez",
    ) = RequestDetailUiState(
        content = Loadable.Success(RequestDetailViewModel.detailOf(loan)),
        borrowerName = name,
        borrowerShortAddress = com.latifi.wallet.ui.profile.ProfileViewModel.shorten(loan.borrower),
        history = history,
        ownAddress = own,
        rates = rates,
    )

    private fun show(state: RequestDetailUiState, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val screen = @androidx.compose.runtime.Composable {
                    RequestDetailScreen(
                        state = state,
                        onReload = { reloads += 1 },
                        onReloadHistory = { historyReloads += 1 },
                        onBack = {},
                        onFund = { funded += 1 },
                    )
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun startsWith(prefix: String) = SemanticsMatcher("content description starts with \"$prefix\"") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(prefix) } == true
    }

    @Test
    fun theReputationChipIsSpokenWithTheLevelAndTheScore() {
        show(state())

        val level = text(R.string.rep_level_trusted)
        rule.onNodeWithContentDescription(text(R.string.rep_chip_a11y, level, 620)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyRowReadsAsOnePhrase() {
        show(state())

        listOf(
            R.string.detail_rate, R.string.detail_term, R.string.detail_installments, R.string.detail_installment_amount,
            R.string.detail_total, R.string.detail_earn,
        ).forEach { label ->
            rule.onNode(startsWith("${text(label)}, ")).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("${text(R.string.detail_borrower)}, Lucia Fernandez, 0x1111...1111")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAPublicNameTheBorrowerRowShowsTheShortAddress() {
        show(state(name = null))

        rule.onNodeWithContentDescription("${text(R.string.detail_borrower)}, 0x1111...1111").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fiveHistoryRowsRender() {
        show(state())

        rule.onAllNodes(startsWith("${text(R.string.rep_event_ontime)}") or startsWith(text(R.string.rep_event_profile)))
            .assertCountEquals(5)
        rule.onNodeWithText(text(R.string.detail_history_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aNewBorrowerShowsTheEmptyHistoryState() {
        show(state(history = RecentHistory.NewBorrower))

        rule.onNodeWithText(text(R.string.detail_history_new)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aHistoryErrorOffersItsOwnReloadAndTheLoanStays() {
        show(state(history = RecentHistory.Error(ErrorKind.Offline)))

        rule.onNodeWithText(text(R.string.retry_reload)).performScrollTo().performClick()
        assertEquals(1, historyReloads)
        rule.onNode(startsWith("${text(R.string.detail_rate)}, ")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theFixedTermsLineIsPresent() {
        show(state())

        rule.onNodeWithText(text(R.string.detail_terms_fixed)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fundRequestIsTheOnlyPrimaryAndTheTapReportsIt() {
        show(state())

        rule.onAllNodesWithText(text(R.string.fund_cta)).assertCountEquals(1)
        rule.onNodeWithText(text(R.string.fund_cta)).assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, funded)
    }

    @Test
    fun theOwnRequestDisablesFundWithAVisibleReason() {
        show(state(own = openLoan.borrower))

        rule.onNodeWithText(text(R.string.fund_cta)).assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.fund_own_request)).assertIsDisplayed()
        assertEquals(0, funded)
    }

    @Test
    fun aRequestThatIsNotOpenShowsItsStatusAndNoPrimary() {
        val active = FakeData.loan(clock, 22, FakeData.LUCIA, FakeData.LENDER_A, 250, 1000, 30, 3, LoanStatus.ACTIVE, paid = 1, requestedDaysAgo = 13, fundedDaysAgo = 12)
        show(state(loan = active))

        rule.onNodeWithText(text(R.string.fund_cta)).assertDoesNotExist()
        rule.onNodeWithContentDescription(text(R.string.status_pending_a11y)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingLoanShowsTheNotFoundCopyAndNoPrimary() {
        show(RequestDetailUiState(content = Loadable.Error(ErrorKind.NotFound)))

        rule.onNodeWithText(text(R.string.err_not_found)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.fund_cta)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        assertEquals(1, reloads)
    }

    @Test
    fun atTwoHundredPercentTextTheRowsWrapAndALongBorrowerNameIsNotTruncated() {
        val longName = "Maria de los Angeles Fernandez de la Torre y Benavides Quispe Rodriguez Alvarez"
        assertTrue(longName.length >= 78)
        show(state(name = longName), fontScale = 2.0f)

        rule.onNodeWithContentDescription("${text(R.string.detail_borrower)}, $longName, 0x1111...1111")
            .performScrollTo().assertIsDisplayed()
        rule.assertNoTruncatedText()
    }
}
