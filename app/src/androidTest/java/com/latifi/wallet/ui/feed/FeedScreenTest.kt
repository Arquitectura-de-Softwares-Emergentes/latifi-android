package com.latifi.wallet.ui.feed

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.domain.feed.AmountRange
import com.latifi.wallet.domain.feed.FeedFilters
import com.latifi.wallet.domain.feed.FeedSort
import com.latifi.wallet.domain.feed.Risk
import com.latifi.wallet.domain.feed.TermLimit
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.components.FILTER_CHECK_TAG
import com.latifi.wallet.ui.components.SKELETON_CARD_TAG
import com.latifi.wallet.ui.theme.LatiFiTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant

/**
 * S12 on a device: the Sort and Filters buttons with their spoken state, the group headings, the
 * chips, the polite counter and its loading variant, Clear filters and the focus it returns, every
 * state, the two-target card with its Fund button, the own-request reason, 200 percent text, both
 * themes and a picture of the feed with filters applied in each theme for the workflow.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class FeedScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val events = mutableListOf<FeedEvent>()
    private val opened = mutableListOf<BigInteger>()
    private val funded = mutableListOf<BigInteger>()
    private val effects = Channel<FeedEffect>(Channel.BUFFERED)
    private var uiState by mutableStateOf(FeedUiState())

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val asOf = Instant.parse("2026-11-10T12:00:00Z")
    private val ready = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = false)), asOf, stale = false),
    )
    private val staleRates = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = true)), asOf, stale = true),
    )

    private fun card(
        id: Long,
        tokens: Long,
        borrower: String,
        name: String?,
        score: Int,
        bps: Int = 700,
        term: Int = 60,
        installments: Int = 6,
        own: Boolean = false,
    ) = RequestCardModel(
        loanId = BigInteger.valueOf(id),
        borrower = borrower,
        name = name,
        amount = TokenAmount.ofTokens(tokens),
        level = ReputationLevel.fromScore(score),
        score = score,
        rateBps = bps,
        termDays = term,
        installments = installments,
        isOwn = own,
    )

    private val lucia = card(1, 500, FakeData.LUCIA, "Lucia Fernandez", 820)
    private val cards = listOf(
        lucia,
        card(2, 200, FakeData.MATEO, "Mateo Quispe", 650, bps = 1200, term = 30, installments = 2),
        card(4, 80, FakeData.CAMILA, null, 450, bps = 1500, term = 45, installments = 1),
        card(12, 120, FakeData.DEMO_ADDRESS, null, 620, bps = 800, term = 45, installments = 2, own = true),
    )

    private fun state(
        content: FeedContent = FeedContent.Items(cards, hasMore = false),
        filters: FeedFilters = FeedFilters(),
        counter: Long? = 12,
        rates: RatesState = ready,
        refreshFailed: Boolean = false,
    ) = FeedUiState(filters = filters, content = content, counter = counter, rates = rates, refreshFailed = refreshFailed)

    /** A small stand-in for the view model, so a tap changes what the stateless screen shows. */
    private fun reduce(event: FeedEvent) {
        events += event
        val current = uiState
        val f = current.filters
        uiState = when (event) {
            is FeedEvent.SelectRisk -> current.copy(filters = f.toggleRisk(event.chip))
            is FeedEvent.SelectTerm -> current.copy(filters = f.toggleTerm(event.chip))
            is FeedEvent.SelectAmount -> current.copy(filters = f.toggleAmount(event.chip))
            is FeedEvent.SetSort -> current.copy(filters = f.copy(sort = event.sort))
            is FeedEvent.RemoveFilter -> {
                effects.trySend(FeedEffect.FilterRemoved)
                current.copy(
                    filters = when (event.group) {
                        FilterGroup.Risk -> f.copy(risk = null)
                        FilterGroup.Term -> f.copy(term = null)
                        FilterGroup.Amount -> f.copy(amount = null)
                    },
                )
            }
            FeedEvent.ClearFilters -> {
                effects.trySend(FeedEffect.FocusCounter)
                current.copy(filters = f.cleared())
            }
            FeedEvent.RefreshFailedShown -> current.copy(refreshFailed = false)
            else -> current
        }
    }

    private fun show(initial: FeedUiState, darkTheme: Boolean = false, fontScale: Float? = null) {
        uiState = initial
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = darkTheme) {
                val screen = @androidx.compose.runtime.Composable {
                    FeedScreen(
                        state = uiState,
                        onEvent = ::reduce,
                        onOpen = { opened += it },
                        onFund = { funded += it },
                        effects = effects.receiveAsFlow(),
                    )
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun chip(group: Int, label: String, selected: Boolean) =
        text(if (selected) R.string.chip_selected_a11y else R.string.chip_not_selected_a11y, text(group), label)

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun openFilters() {
        rule.onNode(hasText(text(R.string.filters_toggle), substring = true) and hasClickAction()).performClick()
    }

    @Test
    fun theSortButtonReadsTheCurrentSortAndChoosingRateReadsSortRate() {
        show(state())

        val button = rule.onNodeWithText(text(R.string.sort_label, text(R.string.sort_reputation)))
        button.assert(polite)
        button.assertHasClickAction()
        button.assertHeightIsAtLeast(48.dp)
        button.performClick()

        val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        rule.onNodeWithText(text(R.string.sort_reputation)).assert(radioRole)
        rule.onNodeWithText(text(R.string.sort_reputation)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithText(text(R.string.sort_rate)).assert(radioRole)
        rule.onNodeWithText(text(R.string.sort_rate)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        rule.onNodeWithText(text(R.string.sort_term)).assertExists()
        rule.onNodeWithText(text(R.string.sort_recent)).assertExists()
        rule.onNodeWithText(text(R.string.sort_rate)).performClick()

        rule.onNodeWithText(text(R.string.sort_label, text(R.string.sort_rate))).assertExists()
        assertEquals(listOf<FeedEvent>(FeedEvent.SetSort(FeedSort.Rate)), events)
    }

    @Test
    fun theFiltersButtonReadsHowManyAreActiveAndExposesExpandedOrCollapsed() {
        show(state(filters = FeedFilters(risk = Risk.Low, term = TermLimit.UpTo30)))

        val button = rule.onNodeWithText("Filters, 2 active")
        button.assertHeightIsAtLeast(48.dp)
        button.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        button.performClick()

        rule.onNodeWithText("Filters, 2 active")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        rule.onNodeWithText(text(R.string.risk_help)).assertIsDisplayed()
    }

    @Test
    fun withNoFilterTheButtonJustSaysFilters() {
        show(state())

        rule.onNodeWithText(text(R.string.filters_toggle)).assertExists()
    }

    @Test
    fun theThreeGroupHeadingsAreHeadings() {
        show(state())
        openFilters()

        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        listOf(R.string.group_risk, R.string.group_term, R.string.group_amount).forEach {
            rule.onNode(hasText(text(it)) and heading).assertExists()
        }
    }

    @Test
    fun aSelectedChipIsSpokenWithItsGroupAndStateAndHasACheck() {
        show(state(filters = FeedFilters(risk = Risk.Low)))
        openFilters()

        val selected = chip(R.string.group_risk, text(R.string.risk_low), selected = true)
        val notSelected = chip(R.string.group_risk, text(R.string.risk_lowest), selected = false)
        // The chip shows in the removable row and in its group.
        rule.onAllNodesWithContentDescription(selected).assertCountEquals(2)
        rule.onNodeWithContentDescription(notSelected).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription(notSelected)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        rule.onAllNodesWithTag(FILTER_CHECK_TAG, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun theTermAndAmountChipsUseTheirOwnWords() {
        show(state())
        openFilters()

        val upTo45 = context.resources.getQuantityString(R.plurals.term_up_to, 45, 45)
        rule.onNodeWithContentDescription(chip(R.string.group_term, upTo45, false)).assertExists()
        rule.onNodeWithContentDescription(chip(R.string.group_amount, text(R.string.amount_between), false)).assertExists()
        rule.onNodeWithContentDescription(chip(R.string.group_amount, text(R.string.amount_over), false)).assertExists()
    }

    @Test
    fun tappingAChipSendsItsEventAndTappingItAgainClearsIt() {
        show(state())
        openFilters()

        rule.onNodeWithContentDescription(chip(R.string.group_risk, text(R.string.risk_medium), false)).performClick()
        assertEquals(Risk.Medium, uiState.filters.risk)
        rule.onAllNodesWithContentDescription(chip(R.string.group_risk, text(R.string.risk_medium), true))[1].performClick()
        assertEquals(null, uiState.filters.risk)
        assertEquals(
            listOf<FeedEvent>(FeedEvent.SelectRisk(Risk.Medium), FeedEvent.SelectRisk(Risk.Medium)),
            events,
        )
    }

    @Test
    fun removingAnActiveChipFromTheRowSaysFilterRemoved() {
        show(state(filters = FeedFilters(amount = AmountRange.Under100)))

        rule.onAllNodesWithContentDescription(chip(R.string.group_amount, text(R.string.amount_under), true))[0].performClick()

        assertEquals(listOf<FeedEvent>(FeedEvent.RemoveFilter(FilterGroup.Amount)), events)
        rule.onNodeWithText(text(R.string.filter_removed_a11y)).assertIsDisplayed()
    }

    @Test
    fun theCounterIsAPoliteLiveRegionThatReadsTwelveRequests() {
        show(state(counter = 12))

        rule.onNodeWithText("12 requests").assert(polite)
    }

    @Test
    fun theCounterReadsOneRequestInTheSingular() {
        show(state(counter = 1, content = FeedContent.Items(cards.take(1), hasMore = false)))

        rule.onNodeWithText("1 request").assert(polite)
    }

    @Test
    fun whileLoadingTheCounterIsPlainTextAndNotLive() {
        show(state(content = FeedContent.Loading, counter = null))

        rule.onNode(hasText(text(R.string.loading_requests)))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun loadingShowsThreeSkeletonCardsAndDisablesTheHeaderControls() {
        show(state(content = FeedContent.Loading, counter = null))

        rule.onNodeWithContentDescription(text(R.string.loading_requests)).assertExists()
        rule.onAllNodesWithTag(SKELETON_CARD_TAG, useUnmergedTree = true).assertCountEquals(3)
        rule.onNodeWithText(text(R.string.sort_label, text(R.string.sort_reputation))).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.filters_toggle)).assertIsNotEnabled()
    }

    @Test
    fun clearFiltersIsAbsentWhenOnlyTheSortChanged() {
        show(state(filters = FeedFilters(sort = FeedSort.Rate)))

        rule.onNodeWithText(text(R.string.filter_clear)).assertDoesNotExist()
    }

    @Test
    fun clearFiltersAppearsWithAFilterResetsTheGroupsKeepsTheSortAndFocusesTheCounter() {
        show(state(filters = FeedFilters(Risk.Low, TermLimit.UpTo30, AmountRange.Over500, FeedSort.Rate)))

        rule.onNodeWithText(text(R.string.filter_clear)).performClick()
        rule.waitForIdle()

        assertEquals(FeedFilters(sort = FeedSort.Rate), uiState.filters)
        rule.onNodeWithText(text(R.string.filter_clear)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.sort_label, text(R.string.sort_rate))).assertExists()
        rule.onNodeWithText("12 requests").assertIsFocused()
    }

    @Test
    fun theNoMatchStateHasExactlyOneClearFiltersButtonAndItIsTheOnlyAction() {
        show(state(content = FeedContent.FilteredEmpty, filters = FeedFilters(risk = Risk.Lowest), counter = 0))

        rule.onNodeWithText(text(R.string.feed_filtered_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.feed_filtered_empty_body)).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.filter_clear)).assertCountEquals(1)
        rule.onNodeWithText(text(R.string.fund_card_cta)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.filter_clear)).performClick()
        assertEquals(listOf<FeedEvent>(FeedEvent.ClearFilters), events)
    }

    @Test
    fun theNoRequestsStateHasNoActionButton() {
        show(state(content = FeedContent.Empty, counter = 0))

        rule.onNodeWithText(text(R.string.feed_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.feed_empty_body)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.filter_clear)).assertDoesNotExist()
    }

    @Test
    fun theErrorPanelOffersReload() {
        show(state(content = FeedContent.Error(ErrorKind.Offline), counter = null))

        rule.onNodeWithText(text(R.string.err_offline_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        assertEquals(listOf<FeedEvent>(FeedEvent.Reload), events)
    }

    @Test
    fun aFailedRefreshWithDataShowsTheSnackbarAndKeepsTheCards() {
        show(state(refreshFailed = true))

        rule.onNodeWithText(text(R.string.refresh_failed)).assertIsDisplayed()
        rule.onNodeWithText("500.00 tUSD", substring = true).assertExists()
    }

    @Test
    fun loadMoreAppearsWhenThereAreMoreRequests() {
        show(state(content = FeedContent.Items(cards, hasMore = true), counter = 25))

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(R.string.load_more)))
        rule.onNodeWithText(text(R.string.load_more)).assertIsDisplayed()
    }

    @Test
    fun aCardIsOneMergedSummaryAndASeparateFundButtonAndNeverMoreThanTwoTargets() {
        show(state(content = FeedContent.Items(listOf(lucia), hasMore = false), counter = 1))

        val spoken = listOf(
            TokenAmount.ofTokens(500).format(locale),
            text(R.string.local_estimate, LocalCurrency.PEN.format(BigDecimal("1875.00"), locale)),
            text(R.string.requested_by, "Lucia Fernandez"),
            text(R.string.rep_chip_a11y, text(R.string.rep_level_excellent), 820),
            text(R.string.rate_per_year, PercentFormat.fromBasisPoints(700, locale)),
            context.resources.getQuantityString(R.plurals.term_days, 60, 60),
            context.resources.getQuantityString(R.plurals.installments_count, 6, 6),
        ).joinToString(", ")

        val summary = rule.onNodeWithContentDescription(spoken)
        summary.assertHasClickAction()
        rule.onNode(hasAnyAncestor(hasContentDescription(spoken)) and hasClickAction(), useUnmergedTree = true)
            .assertDoesNotExist()

        val fund = rule.onNodeWithContentDescription(text(R.string.fund_card_a11y, "500.00 tUSD"))
        fund.assertHasClickAction()
        fund.assertHeightIsAtLeast(48.dp)

        summary.performClick()
        fund.performClick()
        assertEquals(listOf(BigInteger.ONE), opened)
        assertEquals(listOf(BigInteger.ONE), funded)
    }

    @Test
    fun aBorrowerWithNoNameYetShowsTheFallbackInsteadOfRequestedBy() {
        show(state(content = FeedContent.Items(listOf(cards[2]), hasMore = false), counter = 1))

        rule.onNodeWithText(text(R.string.name_not_shared)).assertExists()
        rule.onNodeWithText(text(R.string.requested_by, "")).assertDoesNotExist()
    }

    @Test
    fun theFundButtonOfTheOwnRequestIsDisabledWithAVisibleReason() {
        show(state(content = FeedContent.Items(listOf(cards[3]), hasMore = false), counter = 1))

        rule.onNodeWithContentDescription(text(R.string.fund_card_a11y, "120.00 tUSD")).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.fund_own_request)).assertIsDisplayed()
    }

    @Test
    fun aStaleRateShowsOneCaptionForTheWholeList() {
        show(state(rates = staleRates))

        rule.onNode(hasText("It may be out of date.", substring = true)).assertExists()
    }

    @Test
    fun atTwoHundredPercentTextTheFilterGroupsAndTheCardsWrapAndNothingIsTruncated() {
        show(state(filters = FeedFilters(risk = Risk.Low, term = TermLimit.UpTo45)), fontScale = 2.0f)
        openFilters()

        rule.onNodeWithText(text(R.string.risk_help)).assertExists()
        rule.assertNoTruncatedText()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(R.string.fund_card_cta)))
        rule.assertNoTruncatedText()
    }

    @Test
    fun theFeedWithFiltersRendersInTheLightThemeAndLeavesAPicture() {
        show(state(filters = FeedFilters(risk = Risk.Medium, term = TermLimit.UpTo60)), darkTheme = false)
        openFilters()

        rule.onNodeWithText(text(R.string.group_amount)).assertIsDisplayed()
        saveScreenshot("feed-filters-light.png")
    }

    @Test
    fun theFeedWithFiltersRendersInTheDarkThemeAndLeavesAPicture() {
        show(state(filters = FeedFilters(risk = Risk.Medium, term = TermLimit.UpTo60)), darkTheme = true)
        openFilters()

        rule.onNodeWithText(text(R.string.group_amount)).assertIsDisplayed()
        saveScreenshot("feed-filters-dark.png")
    }

    /**
     * Writes the rendered screen as a PNG to the additional test output folder, which the workflow
     * uploads (UI-SPEC Verification Plan, screenshot row for S12). A device that cannot write there
     * keeps the test green, and the bitmap itself is still checked to hold pixels.
     */
    private fun saveScreenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("the screenshot is empty", bitmap.width > 0 && bitmap.height > 0)
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)?.path
            ?: return
        runCatching {
            File(dir).mkdirs()
            FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
