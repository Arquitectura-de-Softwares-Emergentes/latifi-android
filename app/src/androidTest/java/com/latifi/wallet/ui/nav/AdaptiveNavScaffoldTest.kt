package com.latifi.wallet.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell at normal text shows the bottom bar (the emulator is a compact phone), and from
 * font scale 1.3 the drawer replaces it. Scales are set with [WithFontScale] so the runs do
 * not depend on a device setting.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveNavScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun label(res: Int) = context.getString(res)

    /** A tab item is the node that has the label and a selected state. The screen title has no selected state. */
    private fun navItem(res: Int): SemanticsMatcher = hasText(label(res)) and isSelectable()

    private fun setShell(role: AppRole, fontScale: Float) {
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                WithFontScale(fontScale) {
                    var selected by remember { mutableStateOf(TabDestination.Home) }
                    AdaptiveNavScaffold(
                        role = role,
                        selected = selected,
                        onSelect = { selected = it },
                    ) {
                        ScreenScaffold(title = stringResource(selected.label)) { padding ->
                            Box(Modifier.padding(padding))
                        }
                    }
                }
            }
        }
    }

    private fun assertBarHidden(role: AppRole) {
        for (tab in tabsFor(role)) {
            val items = rule.onAllNodes(navItem(tab.label))
            val count = items.fetchSemanticsNodes().size
            for (i in 0 until count) items[i].assertIsNotDisplayed()
        }
    }

    private fun assertDrawerFlow(fontScale: Float) {
        setShell(AppRole.Borrower, fontScale)
        assertBarHidden(AppRole.Borrower)

        val menu = rule.onNodeWithContentDescription(label(R.string.menu_open))
        menu.assertIsDisplayed()
        menu.assertHeightIsAtLeast(48.dp)
        menu.performClick()

        for (tab in tabsFor(AppRole.Borrower)) {
            val item = rule.onNode(navItem(tab.label))
            item.assertIsDisplayed()
            item.assertHeightIsAtLeast(56.dp)
        }
    }

    @Test
    fun borrowerBarShowsTheFourLabelsAtNormalText() {
        setShell(AppRole.Borrower, 1.0f)
        for (tab in tabsFor(AppRole.Borrower)) rule.onNode(navItem(tab.label)).assertIsDisplayed()
        rule.onAllNodes(navItem(R.string.nav_invest)).assertCountEquals(0)
        rule.onAllNodes(navItem(R.string.nav_portfolio)).assertCountEquals(0)
    }

    @Test
    fun noMenuButtonExistsAtNormalText() {
        setShell(AppRole.Borrower, 1.0f)
        rule.onAllNodesWithContentDescription(label(R.string.menu_open)).assertCountEquals(0)
    }

    @Test
    fun selectingLoansMarksItSelected() {
        setShell(AppRole.Borrower, 1.0f)
        rule.onNode(navItem(R.string.nav_home)).assertIsSelected()
        rule.onNode(navItem(R.string.nav_loans)).performClick()
        rule.onNode(navItem(R.string.nav_loans)).assertIsSelected()
        // The screen title follows the selection.
        rule.onNode(hasText(label(R.string.nav_loans)) and isHeading()).assertIsDisplayed()
    }

    @Test
    fun lenderBarShowsInvestAndPortfolio() {
        setShell(AppRole.Lender, 1.0f)
        for (tab in tabsFor(AppRole.Lender)) rule.onNode(navItem(tab.label)).assertIsDisplayed()
        rule.onAllNodes(navItem(R.string.nav_loans)).assertCountEquals(0)
        rule.onAllNodes(navItem(R.string.nav_reputation)).assertCountEquals(0)
    }

    @Test
    fun drawerReplacesTheBarAtScaleOnePointThree() = assertDrawerFlow(1.3f)

    @Test
    fun drawerReplacesTheBarAtScaleOnePointFive() = assertDrawerFlow(1.5f)

    @Test
    fun drawerReplacesTheBarAtScaleTwo() = assertDrawerFlow(2.0f)

    @Test
    fun lenderDrawerListsInvestAndPortfolioAtScaleTwo() {
        setShell(AppRole.Lender, 2.0f)
        rule.onNodeWithContentDescription(label(R.string.menu_open)).performClick()
        for (tab in tabsFor(AppRole.Lender)) rule.onNode(navItem(tab.label)).assertIsDisplayed()
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun accessibilityChecksPassOnTheBar() {
        rule.enableAccessibilityChecks()
        setShell(AppRole.Borrower, 1.0f)
        rule.onNode(navItem(R.string.nav_loans)).performClick()
        rule.onNode(navItem(R.string.nav_loans)).assertIsSelected()
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun accessibilityChecksPassOnTheDrawer() {
        rule.enableAccessibilityChecks()
        setShell(AppRole.Borrower, 2.0f)
        rule.onNodeWithContentDescription(label(R.string.menu_open)).performClick()
        rule.onNode(navItem(R.string.nav_loans)).performClick()
    }
}
