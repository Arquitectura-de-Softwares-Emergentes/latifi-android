package com.latifi.wallet.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.latifi.wallet.ui.loans.LoanDetailScreen
import com.latifi.wallet.ui.loans.LoanDetailViewModel

/**
 * The loan details (S08). It opens above the tabs from a card. The loan id travels as digits and
 * [LoanDetailViewModel] parses it again, so a malformed id shows the not found copy.
 */
fun NavGraphBuilder.loansGraph(navController: NavHostController) {
    composable(
        Routes.LOAN_DETAIL,
        arguments = listOf(navArgument(RouteArgs.LOAN_ID) { type = NavType.StringType }),
    ) { LoanDetailRoute(navController) }
}

@Composable
private fun LoanDetailRoute(navController: NavHostController) {
    val viewModel: LoanDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoanDetailScreen(
        state = state,
        onReload = viewModel::reload,
        onBack = { navController.popBackStack() },
        onPay = { id -> navController.navigate(Routes.pay(id)) },
    )
}
