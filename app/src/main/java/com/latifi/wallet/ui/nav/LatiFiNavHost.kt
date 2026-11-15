package com.latifi.wallet.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.latifi.wallet.ui.components.rememberMotion
import com.latifi.wallet.ui.root.RootViewModel
import com.latifi.wallet.ui.theme.LatiFiMotion

/**
 * The one NavHost of the app. It waits for [RootViewModel] to resolve the start destination, so
 * the first frame already shows the right screen. Feature graphs are added by NavGraphBuilder
 * extension functions, and [extraGraphs] lets a later plan (the wallet graph of plan 05-14) add
 * destinations without editing this file.
 */
@Composable
fun LatiFiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    extraGraphs: NavGraphBuilder.() -> Unit = {},
) {
    val root: RootViewModel = hiltViewModel()
    val start by root.startDestination.collectAsStateWithLifecycle()
    // A State object keeps the graph builder lambda stable when the role changes.
    val roleState = root.role.collectAsStateWithLifecycle()
    val startDestination = start ?: return

    val motion = rememberMotion()
    val offsetPx = with(LocalDensity.current) { LatiFiMotion.SharedAxisOffsetDp.dp.roundToPx() }
    // Shared axis X between screens that sit above the tabs, instant under reduced motion. The pop
    // transitions also make the system predictive back gesture show the screen underneath.
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { motion.axisEnter(forward = true, offsetPx = offsetPx) },
        exitTransition = { motion.axisExit(forward = true, offsetPx = offsetPx) },
        popEnterTransition = { motion.axisEnter(forward = false, offsetPx = offsetPx) },
        popExitTransition = { motion.axisExit(forward = false, offsetPx = offsetPx) },
    ) {
        onboardingGraph(navController, returning = startDestination == Routes.CONNECT)
        profileGraph(navController)
        mainTabsGraph(navController) { roleState.value }
        loansGraph(navController)
        lenderGraph(navController, onFund = { id -> navController.navigate(Routes.fund(id)) })
        flowsGraph(navController)
        extraGraphs()
    }
}
