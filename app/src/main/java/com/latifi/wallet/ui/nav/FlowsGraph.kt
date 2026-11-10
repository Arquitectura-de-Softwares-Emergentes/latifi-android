package com.latifi.wallet.ui.nav

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.profile.ProfileGateViewModel
import com.latifi.wallet.ui.profile.rememberRequireProfile
import com.latifi.wallet.ui.request.RequestLoanActions
import com.latifi.wallet.ui.request.RequestLoanEffect
import com.latifi.wallet.ui.request.RequestLoanScreen
import com.latifi.wallet.ui.request.RequestLoanViewModel

/** The tab the Loans tab asks the container to open after a published request. */
const val OPEN_TAB_KEY = "open_tab"

/**
 * The flows that move money or publish a request. Each one opens above the tabs. The tab wiring
 * reaches them through [rememberGatedNavigation], so a person with no profile saves it first.
 */
fun NavGraphBuilder.flowsGraph(navController: NavHostController) {
    composable(Routes.REQUEST_LOAN) { RequestLoanRoute(navController) }
}

/**
 * An entry callback that passes the profile gate and then opens [route]. A missing profile opens the
 * form and the navigation goes on by itself once the details are saved. A failed check opens
 * nothing and says so.
 */
@Composable
fun rememberGatedNavigation(rootController: NavHostController, route: () -> String): () -> Unit {
    val gate: ProfileGateViewModel = hiltViewModel()
    val context = LocalContext.current
    return rememberRequireProfile(
        navController = rootController,
        gate = gate.gate,
        session = gate.sessions,
        onReady = { rootController.navigate(route()) },
        onError = { error ->
            Toast.makeText(context, ErrorCopy.textOf(ErrorCopy.kindOf(error)).body, Toast.LENGTH_LONG).show()
        },
    )
}

@Composable
private fun RequestLoanRoute(navController: NavHostController) {
    val viewModel: RequestLoanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RequestLoanEffect.Leave -> navController.popBackStack()
                RequestLoanEffect.OpenLoans -> openLoansTab(navController)
                RequestLoanEffect.OpenProfileForm -> navController.navigate(Routes.profileForm())
                is RequestLoanEffect.ProfileCheckFailed ->
                    Toast.makeText(context, ErrorCopy.textOf(ErrorCopy.kindOf(effect.error)).body, Toast.LENGTH_LONG).show()
                is RequestLoanEffect.FocusField -> Unit
            }
        }
    }

    RequestLoanScreen(
        state = state,
        actions = RequestLoanActions(
            onAmountChange = viewModel::onAmountChange,
            onAmountChip = viewModel::onAmountChip,
            onRateChange = viewModel::onRateChange,
            onTermSelect = viewModel::onTermSelect,
            onDaysChange = viewModel::onDaysChange,
            onDecrease = viewModel::onDecrease,
            onIncrease = viewModel::onIncrease,
            onBlur = viewModel::onBlur,
            onReview = viewModel::onReview,
            onBackToStep1 = viewModel::onBackToStep1,
            onLeaveRequested = viewModel::onLeaveRequested,
            onLeaveConfirmed = viewModel::onLeaveConfirmed,
            onStayEditing = viewModel::onStayEditing,
            onPublish = viewModel::onPublish,
        ),
    )
}

/** Back to the tab container with the Loans tab selected. */
private fun openLoansTab(navController: NavHostController) {
    runCatching { navController.getBackStackEntry(Routes.MAIN).savedStateHandle[OPEN_TAB_KEY] = Routes.LOANS }
    navController.popBackStack(Routes.MAIN, inclusive = false)
}
