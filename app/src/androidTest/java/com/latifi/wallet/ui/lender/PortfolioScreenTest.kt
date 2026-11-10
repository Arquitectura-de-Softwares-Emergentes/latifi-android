package com.latifi.wallet.ui.lender

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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.components.FILTER_CHECK_TAG
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

/** S15 on a device: chips, polite counter, attribution target, the merged card, the states and 200 percent text. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PortfolioScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val events = mutableListOf<PortfolioEvent>()
    private val opened = mutableListOf<BigInteger>()
    private var browsed = 0

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val asOf = Instant.parse("2026-11-10T12:00:00Z")
    private val ready = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = false)), asOf, stale = false),
    )

    private val lent = FakeData.loans(Clock.systemUTC()).filter { it.lender == FakeData.DEMO_ADDRESS }
    private val cards = lent.map { it.toInvestmentModel() } + lent.map { it.toInvestmentModel() }.take(1).map { it.copy(loanId = BigInteger.valueOf(99)) }
    private val running = lent.first { it.loanId == BigInteger.valueOf(8) }

    private fun listState(
        content: PortfolioContent = PortfolioContent.Items(cards, hasMore = false),
        filter: PortfolioFilter = PortfolioFilter.All,
        counter: Long? = cards.size.toLong(),
        rates: RatesState = ready,
        refreshFailed: Boolean = false,
    ) = PortfolioUiState(filter = filter, content = content, counter = counter, rates = rates, refreshFailed = refreshFailed)

    private fun show(state: PortfolioUiState, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val screen = @androidx.compose.runtime.Composable {
                    PortfolioScreen(
                        state = state,
                        onEvent = { events += it },
                        onOpenInvestment = { opened += it },
                        onBrowse = { browsed += 1 },
                    )
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun chip(label: Int, selected: Boolean) =
        text(if (selected) R.string.chip_selected_a11y else R.string.chip_not_selected_a11y, text(R.string.group_status), text(label))

    private fun estimate(amount: TokenAmount): String {
        val value = LocalValueProvider.valueFrom(ready, amount, LocalCurrency.PEN) as LocalValue.Value
        return text(R.string.local_estimate, value.currency.format(value.amount, locale))
    }

    @Test
    fun theFourStatusChipsAreSpokenWithTheirStateAndTheSelectedOneHasACheck() {
        show(listState(filter = PortfolioFilter.Pending))
        val labels = listOf(R.string.status_all, R.string.status_pending, R.string.status_paid, R.string.status_overdue)
        labels.forEach { label ->
            val selected = label == R.string.status_pending
            val node = rule.onNodeWithContentDescription(chip(label, selected))
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (selected) node.assertIsSelected() else node.assertIsNotSelected()
            node.assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithContentDescription(chip(R.string.status_open, selected = false)).assertDoesNotExist()
        rule.onAllNodesWithTag(FILTER_CHECK_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun tappingAChipSendsTheFilterEvent() {
        show(listState())
        rule.onNodeWithContentDescription(chip(R.string.status_paid, selected = false)).performClick()

        assertEquals(listOf<PortfolioEvent>(PortfolioEvent.SelectFilter(PortfolioFilter.Paid)), events)
    }

    @Test
    fun theCounterIsAPoliteLiveRegionThatReadsThreeLoans() {
        show(listState(counter = 3))

        rule.onNodeWithText("3 loans").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
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
    fun aCardIsOneMergedPhraseWithLentExpectedStatusAndTheNextInstallment() {
        val model = running.toInvestmentModel()
        show(listState(content = PortfolioContent.Items(listOf(model), hasMore = false), counter = 1))
        val spoken = listOf(
            "${text(R.string.investment_lent)}: ${model.lent.format(locale)}",
            estimate(model.lent),
            "${text(R.string.investment_expected)}: ${model.expected.format(locale)}",
            estimate(model.expected),
            text(R.string.group_status) + ": " + text(R.string.status_pending),
            text(R.string.loan_next_due, DateFormat.short(running.nextDueDate!!, locale, LocalDate.now())),
        ).joinToString(", ")

        val card = rule.onNodeWithContentDescription(spoken)
        card.assertHasClickAction()
        card.performClick()
        assertEquals(listOf(BigInteger.valueOf(8)), opened)
    }

    @Test
    fun loadingShowsThreeSkeletonCardsUnderOneDescription() {
        show(listState(content = PortfolioContent.Loading, counter = null))

        rule.onNodeWithContentDescription(text(R.string.loading_loans)).assertExists()
        rule.onAllNodesWithTag(SKELETON_CARD_TAG, useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun theEmptyStateOffersBrowseRequests() {
        show(listState(content = PortfolioContent.Empty, counter = 0))

        rule.onNodeWithText(text(R.string.portfolio_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.portfolio_empty_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.home_browse)).performClick()
        assertEquals(1, browsed)
    }

    @Test
    fun theFilteredEmptyStateOffersShowAll() {
        show(listState(content = PortfolioContent.FilteredEmpty, filter = PortfolioFilter.Paid, counter = 0))

        rule.onNodeWithText(text(R.string.loans_filtered_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.loans_show_all)).performClick()
        assertEquals(listOf<PortfolioEvent>(PortfolioEvent.ShowAll), events)
    }

    @Test
    fun theErrorPanelOffersReload() {
        show(listState(content = PortfolioContent.Error(ErrorKind.Offline), counter = null))

        rule.onNodeWithText(text(R.string.err_offline_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        assertEquals(listOf<PortfolioEvent>(PortfolioEvent.Reload), events)
    }

    @Test
    fun atTwoHundredPercentTextTheCardsWrapAndNothingIsTruncated() {
        show(listState(), fontScale = 2.0f)

        rule.onNodeWithText(text(R.string.portfolio_title)).assertIsDisplayed()
        rule.assertNoTruncatedText()
    }

    @Test
    fun atTwoHundredPercentTextTheEmptyStateDoesNotTruncateEither() {
        show(listState(content = PortfolioContent.Empty, counter = 0), fontScale = 2.0f)

        rule.assertNoTruncatedText()
    }
}
