package com.latifi.wallet.ui.nav

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.lender.RequestDetailScreen
import com.latifi.wallet.ui.lender.RequestDetailViewModel
import com.latifi.wallet.ui.profile.rememberRequireProfile
import java.math.BigInteger

/**
 * The lender screens that open above the tabs: the request details (S13) from a feed card. The
 * loan id travels as digits and the view model parses it again, so a malformed id shows the not
 * found copy. [onFund] starts the funding flow once the profile gate passes. The funding screen is
 * registered by plan 05-12, which passes its navigation here. Until then it does nothing, because a
 * tap must never navigate to a route that does not exist.
 */
fun NavGraphBuilder.lenderGraph(
    navController: NavHostController,
    onFund: (BigInteger) -> Unit = {},
) {
    composable(
        Routes.REQUEST_DETAIL,
        arguments = listOf(navArgument(RouteArgs.LOAN_ID) { type = NavType.StringType }),
    ) { RequestDetailRoute(navController, onFund) }
}

@Composable
private fun RequestDetailRoute(navController: NavHostController, onFund: (BigInteger) -> Unit) {
    val viewModel: RequestDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loanId = (state.content as? Loadable.Success)?.data?.loan?.loanId

    // Fund request passes the profile gate first. A missing profile opens the form and the action
    // continues by itself once the details are saved. A failed check never counts as a pass.
    val fundThroughTheGate = rememberRequireProfile(
        navController = navController,
        gate = viewModel.gate,
        session = viewModel.sessions,
        onReady = { loanId?.let(onFund) },
        onError = { error ->
            Toast.makeText(context, ErrorCopy.textOf(ErrorCopy.kindOf(error)).body, Toast.LENGTH_LONG).show()
        },
    )

    RequestDetailScreen(
        state = state,
        onReload = viewModel::reload,
        onReloadHistory = viewModel::reloadHistory,
        onBack = { navController.popBackStack() },
        onFund = fundThroughTheGate,
    )
}
