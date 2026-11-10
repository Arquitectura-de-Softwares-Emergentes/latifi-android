package com.latifi.wallet.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.ui.coins.CoinsSheet
import com.latifi.wallet.ui.connect.WalletHelpSheet
import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.signin.SignInSheetHost
import com.latifi.wallet.ui.signin.SignInViewModel
import com.latifi.wallet.ui.tx.TxStatusOverlay

/**
 * Draws the open overlay above the NavHost. Each overlay gets one branch in the `when`
 * below when its plan builds the sheet, and the same plan removes its name from
 * PENDING_OVERLAYS in RoutesRegistryTest. Until then the overlay draws nothing.
 */
@Composable
fun OverlayHost(controller: OverlayController, reauth: ReauthCoordinator) {
    val current by controller.current.collectAsStateWithLifecycle()
    when (current) {
        Overlays.SIGN_IN -> SignInOverlay(reauth)
        Overlays.WALLET_HELP -> WalletHelpSheet(onClose = { controller.dismiss(Overlays.WALLET_HELP) })
        Overlays.TX_STATUS -> TxStatusOverlay()
        Overlays.COINS -> CoinsSheet()
        else -> Unit
    }
}

/** One view model per run, keyed by the run id, so a second sign-in never reuses a finished one. */
@Composable
private fun SignInOverlay(reauth: ReauthCoordinator) {
    val request by reauth.request.collectAsStateWithLifecycle()
    val run = request ?: return
    val viewModel: SignInViewModel = hiltViewModel(key = "signin-${run.id}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(run.id) { viewModel.start(run.reason) }
    SignInSheetHost(state = state, onEvent = viewModel::onEvent)
}
