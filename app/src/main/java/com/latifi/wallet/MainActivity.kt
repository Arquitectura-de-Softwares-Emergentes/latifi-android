package com.latifi.wallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.ui.nav.LatiFiNavHost
import com.latifi.wallet.ui.overlay.OverlayController
import com.latifi.wallet.ui.overlay.OverlayHost
import com.latifi.wallet.ui.theme.LatiFiTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LatiFiTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    LatiFiNavHost(navController = rememberNavController())
                    OverlayHost(controller = overlayController, reauth = reauthCoordinator)
                }
            }
        }
    }
}
