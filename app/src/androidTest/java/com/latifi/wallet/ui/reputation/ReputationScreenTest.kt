package com.latifi.wallet.ui.reputation

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.model.ReputationSummary
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate

/**
 * S11 on a device: the spoken score, the Levels disclosure and its state, the filter chips, the
 * polite events counter, a history row read as one phrase, the add-your-details state, 200 percent
 * text, both themes and the accessibility checks.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ReputationScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val sent = mutableListOf<ReputationEvents>()
    private var addDetails = 0

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val summary = ReputationSummary(
        score = 620,
        level = ReputationLevel.TRUSTED,
        onTimeCount = 3,
        lateCount = 1,
        defaultCount = 0,
        profileComplete = true,
        updatedAt = Instant.now().minusSeconds(2 * 3_600),
    )

    private fun event(id: Long, type: ReputationEventType, delta: Int, after: Int, loan: Long?, daysAgo: Long) =
        ReputationEvent(
            id = id,
            loanId = loan?.let(BigInteger::valueOf),
            type = type,
            delta = delta,
            scoreAfter = after,
            daysLate = null,
            txHash = null,
            logIndex = null,
            occurredAt = Instant.now().minusSeconds(daysAgo * 86_400),
        )

    private val onTime = event(3, ReputationEventType.REPAID_ON_TIME, 20, 620, 7, 2)
    private val late = event(2, ReputationEventType.REPAID_LATE, -5, 600, 7, 20)
    private val profile = event(1, ReputationEventType.PROFILE_COMPLETED, 0, 605, null, 60)

    private fun ready(
        history: HistoryContent = HistoryContent.Items(listOf(onTime, late, profile), hasMore = false),
        filter: HistoryFilter = HistoryFilter.All,
        counter: Long? = 3,
    ) = ReputationUiState(
        score = ScoreContent.Ready(summary),
        filter = filter,
        history = history,
        counter = counter,
    )

    private fun show(state: ReputationUiState, darkTheme: Boolean = false, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = darkTheme) {
                val screen = @Composable {
                    ReputationScreen(
                        state = state,
                        onEvent = { sent += it },
                        onAddDetails = { addDetails += 1 },
                    )
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private fun chip(label: Int, selected: Boolean) =
        text(if (selected) R.string.chip_selected_a11y else R.string.chip_not_selected_a11y, text(R.string.rep_history_title), text(label))

    private fun spokenRow(event: ReputationEvent, label: Int): String {
        val points = ReputationFormat.points(event.delta)
        val pointsText = context.resources.getQuantityString(R.plurals.rep_event_points, points.magnitude, points.sign, points.magnitude)
        val head = listOfNotNull(text(label), event.loanId?.let { text(R.string.rep_row_loan, it) }).joinToString(", ")
        return text(
            R.string.rep_row_a11y,
            head,
            pointsText,
            text(R.string.rep_row_after, event.scoreAfter),
            ReputationFormat.date(event.occurredAt, locale, LocalDate.now()),
        )
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    }

    @Test
    fun theScoreIsSpokenAsOnePhrase() {
        show(ready())

        rule.onNodeWithContentDescription(text(R.string.rep_score_a11y, text(R.string.rep_level_trusted), 620)).assertIsDisplayed()
    }

    @Test
    fun theScoreShowsAsTextOfThousand() {
        show(ready())

        rule.onNodeWithText(text(R.string.rep_score, 620)).assertIsDisplayed()
    }

    @Test
    fun theThreeCountersAreSpokenWithTheirValue() {
        show(ready())

        rule.onNodeWithContentDescription("${text(R.string.rep_counter_ontime)}, 3").assertIsDisplayed()
        rule.onNodeWithContentDescription("${text(R.string.rep_counter_late)}, 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("${text(R.string.rep_counter_defaulted)}, 0").assertIsDisplayed()
    }

    @Test
    fun theLevelsDisclosureExpandsAndCollapsesAndExposesItsState() {
        show(ready())
        scrollToLevels()
        val title = text(R.string.rep_levels_title)
        val header = hasText(title) and hasClickAction()
        val expand = SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        val collapse = SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)

        rule.onNode(header).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNode(header).assert(expand).assert(!collapse)
        rule.onAllNodesCount(text(R.string.rep_levels_body), 0)

        rule.onNode(header).performClick()
        rule.onNodeWithText(text(R.string.rep_levels_body)).performScrollTo().assertIsDisplayed()
        rule.onNode(header).assert(collapse).assert(!expand)

        rule.onNode(header).performClick()
        rule.onAllNodesCount(text(R.string.rep_levels_body), 0)
        rule.onNode(header).assert(expand)
    }

    @Test
    fun theLevelsHeaderIsAtLeastFortyEightDpHigh() {
        show(ready())
        scrollToLevels()

        rule.onNode(hasText(text(R.string.rep_levels_title)) and hasClickAction()).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun eachFilterChipExposesItsSelectedState() {
        show(ready(filter = HistoryFilter.Late))
        val labels = listOf(
            R.string.rep_filter_all, R.string.rep_filter_ontime, R.string.rep_filter_late, R.string.rep_filter_defaulted,
        )

        labels.forEach { label ->
            val selected = label == R.string.rep_filter_late
            scrollTo(hasContentDescription(chip(label, selected)))
            val node = rule.onNodeWithContentDescription(chip(label, selected))
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (selected) node.assertIsSelected() else node.assertIsNotSelected()
            node.assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun tappingAChipSendsTheFilterEvent() {
        show(ready())
        val target = chip(R.string.rep_filter_late, selected = false)
        scrollTo(hasContentDescription(target))

        rule.onNodeWithContentDescription(target).performClick()

        assertEquals(listOf<ReputationEvents>(ReputationEvents.SelectFilter(HistoryFilter.Late)), sent)
    }

    @Test
    fun theEventsCounterIsAPoliteLiveRegion() {
        show(ready(counter = 12))
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        scrollTo(hasText("12 events"))

        rule.onNodeWithText("12 events").assert(polite)
    }

    @Test
    fun theCounterShowsPlainLoadingTextWhileTheNumberIsMissing() {
        show(ready(counter = null, history = HistoryContent.Loading))
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        scrollTo(hasText(text(R.string.loading_generic)))

        rule.onNodeWithText(text(R.string.loading_generic)).assert(!polite)
    }

    @Test
    fun aHistoryRowIsSpokenAsOnePhrase() {
        show(ready())
        val spoken = spokenRow(onTime, R.string.rep_event_ontime)
        scrollTo(hasContentDescription(spoken))

        rule.onNodeWithContentDescription(spoken).assertIsDisplayed()
    }

    @Test
    fun aRowWithNoLoanDropsTheLoanPart() {
        show(ready())
        val spoken = spokenRow(profile, R.string.rep_event_profile)
        scrollTo(hasContentDescription(spoken))

        rule.onNodeWithContentDescription(spoken).assertIsDisplayed()
    }

    @Test
    fun theUpdatedTimeIsShown() {
        show(ready())

        rule.onNode(hasText(text(R.string.rep_asof, "").trim(), substring = true)).assertIsDisplayed()
    }

    @Test
    fun aMissingProfileShowsTheAddDetailsStateWithAFortyEightDpButton() {
        show(ReputationUiState(score = ScoreContent.NoProfile))

        rule.onNodeWithText(text(R.string.rep_no_profile)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.rep_add_details_cta))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, addDetails)
        rule.onAllNodesCount(text(R.string.rep_history_title), 0)
    }

    @Test
    fun aFailedScoreShowsReload() {
        show(ReputationUiState(score = ScoreContent.Error(ErrorKind.Offline)))

        rule.onNodeWithText(text(R.string.err_offline_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performClick()
        assertEquals(listOf<ReputationEvents>(ReputationEvents.Reload), sent)
    }

    @Test
    fun aFailedHistoryKeepsTheScoreAndReloadsOnlyTheHistory() {
        show(ready(history = HistoryContent.Error(ErrorKind.Server), counter = null))
        // The list is lazy, so the score block must be checked before the scroll moves it away.
        rule.onNodeWithText(text(R.string.rep_score, 620)).assertExists()
        scrollTo(hasText(text(R.string.retry_reload)))

        rule.onNodeWithText(text(R.string.retry_reload)).performClick()

        assertEquals(listOf<ReputationEvents>(ReputationEvents.ReloadHistory), sent)
    }

    @Test
    fun anEmptyHistoryShowsItsTitleAndNextStep() {
        show(ready(history = HistoryContent.Empty, counter = 0))
        scrollTo(hasText(text(R.string.rep_history_empty_title)))

        rule.onNodeWithText(text(R.string.rep_history_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.rep_history_empty_body)).assertIsDisplayed()
    }

    @Test
    fun aFilteredEmptyHistoryOffersToShowAll() {
        show(ready(history = HistoryContent.FilteredEmpty, filter = HistoryFilter.Defaulted, counter = 0))
        scrollTo(hasText(text(R.string.loans_show_all)))

        rule.onNodeWithText(text(R.string.loans_show_all)).performClick()

        assertEquals(listOf<ReputationEvents>(ReputationEvents.ShowAll), sent)
    }

    @Test
    fun atTwoHundredPercentTextTheRowsWrapAndNothingIsTruncated() {
        show(ready(), fontScale = 2.0f)
        scrollTo(hasContentDescription(spokenRow(profile, R.string.rep_event_profile)))

        rule.assertNoTruncatedText()
    }

    @Test
    fun theScoreBlockWrapsAtTwoHundredPercentText() {
        show(ready(), fontScale = 2.0f)

        rule.onNodeWithText(text(R.string.rep_score, 620)).assertExists()
        rule.assertNoTruncatedText()
    }

    @Test
    fun everyLevelRendersItsChipInTheDarkTheme() {
        show(ready(), darkTheme = true)

        rule.onNodeWithContentDescription(text(R.string.rep_score_a11y, text(R.string.rep_level_trusted), 620)).assertIsDisplayed()
    }

    /** The Levels card sits below the score hero, so a short screen has to scroll to compose it. */
    private fun scrollToLevels() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.rep_levels_title)))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesCount(text: String, expected: Int) {
        assertEquals("nodes with text \"$text\"", expected, onAllNodes(hasText(text)).fetchSemanticsNodes().size)
    }
}
