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
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.latifi.wallet.R
import com.latifi.wallet.ui.components.CustomTabLauncher
import com.latifi.wallet.ui.profile.ProfileFormEffect
import com.latifi.wallet.ui.profile.ProfileFormScreen
import com.latifi.wallet.ui.profile.ProfileFormViewModel

/**
 * The profile form (S05). It opens above the screen that needed a profile, or above the Profile
 * tab when the person edits their details. The Profile tab itself is built in `MainTabsGraph`.
 */
fun NavGraphBuilder.profileGraph(navController: NavHostController) {
    composable(
        Routes.PROFILE_FORM,
        arguments = listOf(
            navArgument(RouteArgs.RETURN_TO) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(RouteArgs.GATE) { type = NavType.BoolType; defaultValue = false },
        ),
    ) { ProfileFormRoute(navController) }
}

@Composable
private fun ProfileFormRoute(navController: NavHostController) {
    val viewModel: ProfileFormViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProfileFormEffect.Saved -> {
                    // The result goes to the screen that was blocked, so it can continue by itself.
                    effect.resultKey?.let { key ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(key, true)
                    }
                    Toast.makeText(context, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is ProfileFormEffect.OpenLink -> CustomTabLauncher.open(context, effect.url)
            }
        }
    }

    ProfileFormScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { navController.popBackStack() },
    )
}
