package com.latifi.wallet.ui.reputation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.latifi.wallet.ui.nav.Routes

/**
 * The Reputation tab body. [rootController] is the app NavHost, because the profile form is a
 * destination above the tabs. When the person comes back from it, the score loads again if the
 * screen was showing the add-your-details state.
 */
@Composable
fun ReputationRoute(rootController: NavHostController) {
    val viewModel: ReputationViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ReputationEvents.Resumed) }

    ReputationScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onAddDetails = { rootController.navigate(Routes.profileForm(returnTo = Routes.MAIN)) },
    )
}
