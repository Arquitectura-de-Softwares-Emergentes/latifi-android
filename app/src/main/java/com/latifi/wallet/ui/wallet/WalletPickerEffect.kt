package com.latifi.wallet.ui.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import com.latifi.wallet.data.wallet.AppKitPickerBridge
import com.reown.appkit.ui.openAppKit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens the AppKit picker when the wallet gateway asks for it, and tells the gateway when the
 * picker is gone again. "Gone" means the screen that was showing when the picker opened is back on
 * top, so the gateway can stop waiting if the person leaves without choosing a wallet.
 */
@Composable
fun WalletPickerEffect(nav: NavController, bridge: AppKitPickerBridge) {
    LaunchedEffect(nav, bridge) {
        var watcher: Job? = null
        bridge.requests.collect {
            watcher?.cancel()
            val before = nav.currentBackStackEntry?.destination?.route
            nav.openAppKit(shouldOpenChooseNetwork = false, onError = { bridge.openFailed() })
            watcher = launch { watchUntilClosed(nav, before, bridge) }
        }
    }
}

private const val SHOW_WAIT_MILLIS = 5_000L

private suspend fun watchUntilClosed(nav: NavController, before: String?, bridge: AppKitPickerBridge) {
    val routes = nav.currentBackStackEntryFlow.map { it.destination.route }
    // If the picker never shows (it failed to open), there is nothing to wait for.
    withTimeoutOrNull(SHOW_WAIT_MILLIS) { routes.first { it != before } } ?: return
    routes.first { it == before }
    bridge.pickerClosed()
}
