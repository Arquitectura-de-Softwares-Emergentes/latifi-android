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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.fund.FundEffect
import com.latifi.wallet.ui.fund.FundScreen
import com.latifi.wallet.ui.fund.FundViewModel
import com.latifi.wallet.ui.pay.PayEffect
import com.latifi.wallet.ui.pay.PayScreen
import com.latifi.wallet.ui.pay.PayViewModel
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
    composable(
        Routes.PAY,
        arguments = listOf(navArgument(RouteArgs.LOAN_ID) { type = NavType.StringType }),
    ) { PayRoute(navController) }
    composable(
        Routes.FUND,
        arguments = listOf(navArgument(RouteArgs.LOAN_ID) { type = NavType.StringType }),
    ) { FundRoute(navController) }
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
                RequestLoanEffect.OpenLoans -> openTab(navController, Routes.LOANS)
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

@Composable
private fun PayRoute(navController: NavHostController) {
    val viewModel: PayViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PayEffect.OpenLoans -> openTab(navController, Routes.LOANS)
            }
        }
    }
    PayScreen(
        state = state,
        onConfirm = viewModel::onConfirm,
        onReload = viewModel::reload,
        onBack = { navController.popBackStack() },
    )
}

@Composable
private fun FundRoute(navController: NavHostController) {
    val viewModel: FundViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                FundEffect.OpenRequests -> openTab(navController, Routes.INVEST)
                FundEffect.OpenPortfolio -> openTab(navController, Routes.PORTFOLIO)
            }
        }
    }
    FundScreen(
        state = state,
        onConfirm = viewModel::onConfirm,
        onBackToRequests = viewModel::onBackToRequests,
        onReload = viewModel::reload,
        onBack = { navController.popBackStack() },
    )
}

/** Back to the tab container with [tabRoute] selected. */
private fun openTab(navController: NavHostController, tabRoute: String) {
    runCatching { navController.getBackStackEntry(Routes.MAIN).savedStateHandle[OPEN_TAB_KEY] = tabRoute }
    navController.popBackStack(Routes.MAIN, inclusive = false)
}
