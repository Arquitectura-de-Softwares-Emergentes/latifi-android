package com.latifi.wallet.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.latifi.wallet.ui.components.CustomTabLauncher
import com.latifi.wallet.ui.connect.ConnectEffect
import com.latifi.wallet.ui.connect.ConnectScreen
import com.latifi.wallet.ui.connect.ConnectViewModel
import com.latifi.wallet.ui.onboarding.OnboardingEffect
import com.latifi.wallet.ui.onboarding.OnboardingEvent
import com.latifi.wallet.ui.onboarding.OnboardingScreen
import com.latifi.wallet.ui.onboarding.OnboardingViewModel
import com.latifi.wallet.ui.role.RoleEffect
import com.latifi.wallet.ui.role.RoleScreen
import com.latifi.wallet.ui.role.RoleViewModel

/**
 * The first-run journey: onboarding, the same pager reopened from Profile, Connect wallet and
 * role selection. [returning] is true when the app opened straight on Connect because the person
 * had been through onboarding before, which switches the title to "Reconnect your wallet".
 */
fun NavGraphBuilder.onboardingGraph(navController: NavHostController, returning: Boolean) {
    composable(
        Routes.ONBOARDING,
        arguments = listOf(navArgument(OnboardingViewModel.KEY_SHOW_CONNECT) { type = NavType.BoolType; defaultValue = true }),
    ) { OnboardingRoute(navController) }

    composable(
        Routes.HOW_IT_WORKS,
        arguments = listOf(navArgument(OnboardingViewModel.KEY_SHOW_CONNECT) { type = NavType.BoolType; defaultValue = false }),
    ) { OnboardingRoute(navController) }

    composable(Routes.CONNECT) { ConnectRoute(navController, returning) }

    composable(Routes.ROLE) { RoleRoute(navController) }
}

@Composable
private fun OnboardingRoute(navController: NavHostController) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                OnboardingEffect.GoToConnect -> navController.navigate(Routes.CONNECT) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
                OnboardingEffect.Leave -> navController.popBackStack()
                is OnboardingEffect.OpenLink -> CustomTabLauncher.open(context, effect.url)
            }
        }
    }
    // System back steps to the previous page. On the first page it leaves, as usual.
    BackHandler(enabled = !state.isFirst) { viewModel.onEvent(OnboardingEvent.Back) }

    OnboardingScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun ConnectRoute(navController: NavHostController, returning: Boolean) {
    val viewModel: ConnectViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, returning) { viewModel.setReturning(returning) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ConnectEffect.GoToRole -> navController.navigate(Routes.ROLE) {
                    popUpTo(Routes.CONNECT) { inclusive = true }
                }
                ConnectEffect.GoToMain -> navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.CONNECT) { inclusive = true }
                }
                is ConnectEffect.OpenLink -> CustomTabLauncher.open(context, effect.url)
            }
        }
    }

    ConnectScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun RoleRoute(navController: NavHostController) {
    val viewModel: RoleViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                RoleEffect.GoToMain -> navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.ROLE) { inclusive = true }
                }
            }
        }
    }

    RoleScreen(state = state, onEvent = viewModel::onEvent)
}
