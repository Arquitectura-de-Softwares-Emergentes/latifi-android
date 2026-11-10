package com.latifi.wallet.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.latifi.wallet.domain.profile.GateResult
import com.latifi.wallet.domain.profile.ProfileGate
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The key the profile form writes in the SavedStateHandle of the previous back stack entry once
 * the details are saved (`profile_gate_result`). The calling screen reads it once.
 */
const val PROFILE_GATE_RESULT = "profile_gate_result"

/**
 * Wraps the action of a screen that needs a complete profile (Publish request, Fund request).
 * Call the returned function from the click handler:
 *
 *  - the profile is complete: [onReady] runs at once;
 *  - the profile is missing: the profile form opens with the current route as `returnTo`, and
 *    when the person saves, the form pops back, this helper sees `profile_gate_result` once and
 *    runs [onReady]. The screen was never destroyed, so its own values are still there;
 *  - the check fails (offline, server): [onError] gets the failure and [onReady] does not run.
 *    A failed check is never treated as a pass.
 *
 * [navController] must be the controller that owns the profile form route, and the calling
 * screen must live on that controller's back stack entry (the root controller for the tabs).
 */
@Composable
fun rememberRequireProfile(
    navController: NavHostController,
    gate: ProfileGate,
    session: SessionStore,
    onReady: () -> Unit,
    onError: (Throwable) -> Unit = {},
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)
    // The entry is captured once, while the calling screen is on top. When the form opens, the
    // current entry changes to the form, and this one keeps receiving the result.
    val entry = remember(navController) { navController.currentBackStackEntry }

    // Consume the result once: read it, reset it, then continue the blocked action.
    val resultFlow = remember(entry) {
        entry?.savedStateHandle?.getStateFlow(PROFILE_GATE_RESULT, false) ?: MutableStateFlow(false)
    }
    val result by resultFlow.collectAsStateWithLifecycle()
    LaunchedEffect(result) {
        if (result) {
            entry?.savedStateHandle?.set(PROFILE_GATE_RESULT, false)
            currentOnReady()
        }
    }

    return {
        scope.launch {
            val address = session.current()?.address
            if (address == null) {
                currentOnError(IllegalStateException("No signed-in address"))
                return@launch
            }
            gate.check(address).fold(
                onSuccess = { found ->
                    when (found) {
                        GateResult.Complete -> currentOnReady()
                        GateResult.Missing -> {
                            val here = navController.currentDestination?.route ?: Routes.MAIN
                            navController.navigate(Routes.profileForm(returnTo = here, gate = true))
                        }
                    }
                },
                onFailure = { currentOnError(it) },
            )
        }
    }
}
