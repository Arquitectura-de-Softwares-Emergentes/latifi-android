package com.latifi.wallet.ui.loans

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.components.FILTER_CHECK_TAG
import com.latifi.wallet.ui.components.SKELETON_CARD_TAG
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** S07 on a device: chips, polite counter, attribution target, the merged card, every state and 200 percent text. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class LoansScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val events = mutableListOf<LoansEvent>()
    private val opened = mutableListOf<BigInteger>()
    private var requested = 0

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val asOf = Instant.parse("2026-11-10T12:00:00Z")
    private val ready = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = false)), asOf, stale = false),
    )
    private val staleRates = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = true)), asOf, stale = true),
    )

    private val loans = FakeData.loans(Clock.systemUTC()).filter { it.borrower == FakeData.DEMO_ADDRESS }
    private val cards = loans.sortedByDescending { it.requestedAt }.map { it.toCardModel() }
    private val active = loans.first { it.loanId == BigInteger.valueOf(11) }

    private fun listState(
        content: LoansContent = LoansContent.Items(cards, hasMore = false),
        filter: LoanFilter = LoanFilter.All,
        counter: Long? = cards.size.toLong(),
        rates: RatesState = ready,
        refreshFailed: Boolean = false,
    ) = LoansUiState(filter = filter, content = content, counter = counter, rates = rates, refreshFailed = refreshFailed)

    private fun show(state: LoansUiState, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val screen = @androidx.compose.runtime.Composable {
                    LoansScreen(
                        state = state,
                        onEvent = { events += it },
                        onOpenLoan = { opened += it },
                        onRequestLoan = { requested += 1 },
                    )
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun chip(group: Int, label: Int, selected: Boolean) =
        text(if (selected) R.string.chip_selected_a11y else R.string.chip_not_selected_a11y, text(group), text(label))

    @Test
    fun theFiveStatusChipsAreSpokenWithTheirStateAndTheSelectedOneHasACheck() {
        show(listState(filter = LoanFilter.Pending))
        val labels = listOf(
            R.string.status_all, R.string.status_open, R.string.status_pending, R.string.status_paid, R.string.status_overdue,
        )
        labels.forEach { label ->
            val selected = label == R.string.status_pending
            val node = rule.onNodeWithContentDescription(chip(R.string.group_status, label, selected))
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (selected) node.assertIsSelected() else node.assertIsNotSelected()
            node.assertHeightIsAtLeast(48.dp)
        }
        rule.onAllNodesWithTag(FILTER_CHECK_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun tappingAChipSendsTheFilterEvent() {
        show(listState())
        rule.onNodeWithContentDescription(chip(R.string.group_status, R.string.status_paid, selected = false)).performClick()

        assertEquals(listOf<LoansEvent>(LoansEvent.SelectFilter(LoanFilter.Paid)), events)
    }

    @Test
    fun theCounterIsAPoliteLiveRegionThatReadsThePlural() {
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        show(listState(counter = 1, content = LoansContent.Items(cards.take(1), hasMore = false)))
        rule.onNodeWithText("1 loan").assert(polite)
    }

    @Test
    fun theCounterReadsTwelveLoans() {
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        show(listState(counter = 12, content = LoansContent.Items(cards, hasMore = true)))
        rule.onNodeWithText("12 loans").assert(polite)
    }

    @Test
    fun whileLoadingTheCounterIsPlainTextAndNotLive() {
        show(listState(content = LoansContent.Loading, counter = null))
        rule.onNode(hasText(text(R.string.loading_loans))).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun theAttributionRowIsVisibleWithoutScrollingAndIsA48dpButton() {
        show(listState())
        val row = rule.onNode(hasText("Rates By Exchange Rate API") and hasClickAction())
        row.assertIsDisplayed()
        row.assertHeightIsAtLeast(48.dp)
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun aCardIsOneMergedTargetWithTheSpokenSentence() {
        show(listState(content = LoansContent.Items(listOf(active.toCardModel()), hasMore = false), counter = 1))
        val today = LocalDate.now()
        val estimate = text(R.string.local_estimate, LocalCurrency.PEN.format(BigDecimal("937.50"), locale))
        val spoken = listOf(
            TokenAmount(active.principal).format(locale),
            estimate,
            text(R.string.group_status) + ": " + text(R.string.status_pending),
            text(R.string.rate_per_year, PercentFormat.fromBasisPoints(1000, locale)),
            context.resources.getQuantityString(R.plurals.term_days, 30, 30),
            text(R.string.loan_progress, 1, 3),
            text(R.string.loan_next_due, DateFormat.short(active.nextDueDate!!, locale, today)),
        ).joinToString(", ")

        val card = rule.onNodeWithContentDescription(spoken)
        card.assertHasClickAction()
        rule.onNode(hasAnyAncestor(hasContentDescription(spoken)) and hasClickAction(), useUnmergedTree = true).assertDoesNotExist()
        card.performClick()
        assertEquals(listOf(BigInteger.valueOf(11)), opened)
    }

    @Test
    fun loadingShowsThreeSkeletonCardsUnderOneDescription() {
        show(listState(content = LoansContent.Loading, counter = null))

        rule.onNodeWithContentDescription(text(R.string.loading_loans)).assertExists()
        rule.onAllNodesWithTag(SKELETON_CARD_TAG, useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun theEmptyStateOffersToRequestALoan() {
        show(listState(content = LoansContent.Empty, counter = 0))

        rule.onNodeWithText(text(R.string.loans_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.loans_empty_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.request_title)).performClick()
        assertEquals(1, requested)
    }

    @Test
    fun theFilteredEmptyStateOffersShowAll() {
        show(listState(content = LoansContent.FilteredEmpty, filter = LoanFilter.Paid, counter = 0))

        rule.onNodeWithText(text(R.string.loans_filtered_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.loans_show_all)).performClick()
        assertEquals(listOf<LoansEvent>(LoansEvent.ShowAll), events)
    }

    @Test
    fun theErrorPanelOffersReload() {
        show(listState(content = LoansContent.Error(ErrorKind.Offline), counter = null))

        rule.onNodeWithText(text(R.string.err_offline_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.err_offline_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        assertEquals(listOf<LoansEvent>(LoansEvent.Reload), events)
    }

    @Test
    fun aFailedRefreshWithDataShowsASnackbarAndKeepsTheCards() {
        show(listState(refreshFailed = true))

        rule.onNodeWithText(text(R.string.refresh_failed)).assertIsDisplayed()
        rule.onNodeWithContentDescription(chip(R.string.group_status, R.string.status_all, selected = true)).assertIsDisplayed()
        rule.onNodeWithText("250.00 tUSD", substring = true).assertExists()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        rule.waitForIdle()
        assertEquals(listOf<LoansEvent>(LoansEvent.RefreshFailedShown, LoansEvent.Refresh), events.take(2))
    }

    @Test
    fun loadMoreAppearsWhenThereAreMoreItems() {
        show(listState(content = LoansContent.Items(cards, hasMore = true), counter = 25))

        // The footer is the last item of a lazy list, so scroll to it first.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(R.string.load_more)))
        rule.onNodeWithText(text(R.string.load_more)).assertIsDisplayed()
    }

    @Test
    fun aStaleRateShowsOneCaptionAndTheEstimateStays() {
        show(listState(rates = staleRates))

        rule.onNode(hasText(text(R.string.local_unavailable))).assertDoesNotExist()
        rule.onNode(hasText("It may be out of date.", substring = true)).assertExists()
    }

    @Test
    fun withNoRateTheCardsSayLocalValueUnavailableAndStillShowTheAmount() {
        show(listState(rates = RatesState.Unavailable))

        // The list is lazy, so only the cards that fit on the screen exist. At least one must say it.
        assertTrue(rule.onAllNodes(hasText(text(R.string.local_unavailable))).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("250.00 tUSD", substring = true).assertExists()
    }

    @Test
    fun atTwoHundredPercentTextNothingIsTruncated() {
        show(listState(), fontScale = 2.0f)

        rule.onNodeWithText(text(R.string.loans_title)).assertIsDisplayed()
        rule.assertNoTruncatedText()
    }

    @Test
    fun atTwoHundredPercentTextTheStatesDoNotTruncateEither() {
        show(listState(content = LoansContent.Error(ErrorKind.Server), counter = null), fontScale = 2.0f)

        rule.assertNoTruncatedText()
    }
}
