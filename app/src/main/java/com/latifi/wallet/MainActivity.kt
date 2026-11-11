package com.latifi.wallet

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.data.wallet.AppKitPickerBridge
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.ui.nav.LatiFiNavHost
import com.latifi.wallet.ui.overlay.OverlayController
import com.latifi.wallet.ui.overlay.OverlayHost
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.wallet.AppKitHost
import com.latifi.wallet.ui.wallet.WalletPickerEffect
import com.reown.appkit.ui.appKitGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var config: AppConfig

    @Inject
    lateinit var overlayController: OverlayController

    @Inject
    lateinit var reauthCoordinator: ReauthCoordinator

    @Inject
    lateinit var pickerBridge: AppKitPickerBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LatiFiTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavigationHost()
                    OverlayHost(controller = overlayController, reauth = reauthCoordinator)
                }
            }
        }
    }

    /**
     * Live mode hosts the single NavHost inside the AppKit host and adds the AppKit graph, so the
     * wallet picker is a destination of the same controller. Demo mode has no wallet SDK, so it
     * uses the plain controller and the fake wallet.
     */
    @Composable
    private fun NavigationHost() {
        if (config.mode == AppMode.Live) {
            AppKitHost { nav ->
                LatiFiNavHost(navController = nav, extraGraphs = { appKitGraph(nav) })
                WalletPickerEffect(nav, pickerBridge)
            }
        } else {
            LatiFiNavHost(navController = rememberNavController())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The wallet redirect latifi-wallet://request only brings the task back to the front, which
        // singleTask already does. Nothing is read from the link: the wallet answers over the relay.
        // The activity keeps its own intent, so no data of the link can reach the app.
    }
}
