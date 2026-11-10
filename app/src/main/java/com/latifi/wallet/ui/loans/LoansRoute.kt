package com.latifi.wallet.ui.loans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.latifi.wallet.ui.nav.Routes

/**
 * The Loans tab body. [rootController] is the app NavHost, because the loan details open above
 * the tabs. The filter lives in the view model of this back stack entry, so it is still there when
 * the person comes back from a detail.
 */
@Composable
fun LoansRoute(rootController: NavHostController) {
    val viewModel: LoansViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoansScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenLoan = { id -> rootController.navigate(Routes.loanDetail(id)) },
        // The Request a loan screen is registered by plan 05-12, which wires this button.
        onRequestLoan = {},
    )
}
