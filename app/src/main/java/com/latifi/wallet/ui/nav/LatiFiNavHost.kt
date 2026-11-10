package com.latifi.wallet.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.latifi.wallet.ui.root.RootViewModel

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

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        onboardingGraph(navController, returning = startDestination == Routes.CONNECT)
        profileGraph(navController)
        mainTabsGraph(navController) { roleState.value }
        extraGraphs()
    }
}
