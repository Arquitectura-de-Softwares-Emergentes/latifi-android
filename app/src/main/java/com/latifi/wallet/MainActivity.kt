package com.latifi.wallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var config: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LatiFiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LaunchContent(walletConfigured = config.isWalletConfigured)
                }
            }
        }
    }
}

/** Tracer content, kept internal so the theme smoke test can compose it under both themes. */
@Composable
internal fun LaunchContent(walletConfigured: Boolean) {
    Column(
        modifier = Modifier.safeDrawingPadding().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (!walletConfigured) {
            Text(
                text = stringResource(R.string.wallet_not_configured),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
