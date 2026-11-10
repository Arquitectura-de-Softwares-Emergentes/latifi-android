package com.latifi.wallet.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.latifi.wallet.domain.model.AppRole

/**
 * The one NavHost of the app. Feature graphs are added by NavGraphBuilder extension functions.
 * [extraGraphs] lets a later plan (the wallet graph of plan 05-14) add destinations without
 * editing this file.
 */
@Composable
fun LatiFiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.MAIN,
    initialRole: AppRole = AppRole.Borrower,
    extraGraphs: NavGraphBuilder.() -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        mainTabsGraph(initialRole)
        extraGraphs()
    }
}
