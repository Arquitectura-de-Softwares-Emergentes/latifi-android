package com.latifi.wallet.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.latifi.wallet.ui.components.CustomTabLauncher
import com.latifi.wallet.ui.nav.Routes
import com.latifi.wallet.ui.nav.TabDestination

/**
 * The Profile tab body. [rootController] is the app NavHost, because the form, the help pages
 * and Connect wallet are destinations above the tabs.
 */
@Composable
fun ProfileRoute(rootController: NavHostController) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The form may have changed the name or the contact while this tab was covered.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ProfileEffect.OpenForm -> rootController.navigate(Routes.profileForm(returnTo = Routes.MAIN))
                is ProfileEffect.CopyToClipboard -> copyAddress(context, effect.address)
                ProfileEffect.OpenHowItWorks -> rootController.navigate(Routes.HOW_IT_WORKS)
                is ProfileEffect.OpenLink -> CustomTabLauncher.open(context, effect.url)
                ProfileEffect.SignedOut -> rootController.navigate(Routes.CONNECT) {
                    // An empty back stack: Back from Connect wallet leaves the app, never returns to the tabs.
                    popUpTo(rootController.graph.id) { inclusive = true }
                }
            }
        }
    }

    ProfileScreen(
        state = state,
        onEvent = viewModel::onEvent,
        title = stringResource(TabDestination.Profile.label),
    )
}

/** A wallet address is public chain data, so it goes on the clipboard as plain text. */
private fun copyAddress(context: Context, address: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("address", address))
}
