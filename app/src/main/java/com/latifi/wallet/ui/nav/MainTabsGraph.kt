package com.latifi.wallet.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.ui.feed.FeedRoute
import com.latifi.wallet.ui.home.HomeRoute
import com.latifi.wallet.ui.lender.PortfolioRoute
import com.latifi.wallet.ui.loans.LoansRoute
import com.latifi.wallet.ui.profile.ProfileRoute
import com.latifi.wallet.ui.reputation.ReputationRoute

/**
 * Registers the tab container at [Routes.MAIN]. The container owns a nested NavHost whose
 * destinations are the six tabs, so each tab keeps its own back stack and scroll state while
 * the adaptive scaffold around it shows the four tabs of [role].
 */
fun NavGraphBuilder.mainTabsGraph(rootController: NavHostController, role: () -> AppRole) {
    // [role] is read inside the destination, so a role change recomposes the tabs without
    // rebuilding the graph. [rootController] is the app NavHost: a tab uses it to open screens
    // that sit above the tabs (the profile form, Connect wallet after a disconnect).
    composable(Routes.MAIN) { MainTabs(role(), rootController) }
}

@Composable
internal fun MainTabs(role: AppRole, rootController: NavHostController) {
    val tabController = rememberNavController()
    val entry by tabController.currentBackStackEntryAsState()
    val selected = TabDestination.fromRoute(entry?.destination?.route) ?: TabDestination.Home

    AdaptiveNavScaffold(
        role = role,
        selected = selected,
        onSelect = { tab -> tabController.selectTab(tab) },
    ) {
        NavHost(navController = tabController, startDestination = Routes.HOME) {
            // Each feature plan replaces the placeholder of its tab with the real screen.
            composable(Routes.HOME) { HomeRoute(onBrowse = { tabController.selectTab(TabDestination.Invest) }) }
            composable(Routes.LOANS) { LoansRoute(rootController) }
            composable(Routes.REPUTATION) { ReputationRoute(rootController) }
            composable(Routes.INVEST) {
                FeedRoute(
                    onOpen = { loanId -> rootController.navigate(Routes.requestDetail(loanId)) },
                    // The funding flow is registered by plan 05-12. Until then the card button does nothing.
                    onFund = {},
                )
            }
            composable(Routes.PORTFOLIO) {
                PortfolioRoute(rootController, onBrowse = { tabController.selectTab(TabDestination.Invest) })
            }
            composable(Routes.PROFILE) { ProfileRoute(rootController) }
        }
    }
}

private fun NavHostController.selectTab(tab: TabDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
