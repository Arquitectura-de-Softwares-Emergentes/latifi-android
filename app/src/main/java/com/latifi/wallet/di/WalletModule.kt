package com.latifi.wallet.di

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.latifi.wallet.data.wallet.AppKitClient
import com.latifi.wallet.data.wallet.AppKitPickerBridge
import com.latifi.wallet.data.wallet.AppKitWalletGateway
import com.latifi.wallet.data.wallet.RealAppKitClient
import com.latifi.wallet.data.wallet.UriLauncher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * The wallet side of the graph: the AppKit adapter, the gateway over it, the deep link launcher and
 * the ordered start-up. Nothing here touches the Reown SDK when it is built. The SDK starts in the
 * application start-up, and only when the build is in Live mode.
 */
@Module
@InstallIn(SingletonComponent::class)
object WalletModule {
    @Provides
    @Singleton
    fun provideAppKitClient(): AppKitClient = RealAppKitClient()

    @Provides
    @Singleton
    fun provideUriLauncher(application: Application): UriLauncher = UriLauncher { uri ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            application.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.i("WalletLauncher", "No app handles the wallet link")
        }
    }

    /** Built on first use, so Demo mode never creates it. The first use is after the SDK started. */
    @Provides
    @Singleton
    fun provideAppKitWalletGateway(
        client: AppKitClient,
        bridge: AppKitPickerBridge,
        launcher: UriLauncher,
    ): AppKitWalletGateway = AppKitWalletGateway(
        client = client,
        bridge = bridge,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        launcher = launcher,
    )
}
