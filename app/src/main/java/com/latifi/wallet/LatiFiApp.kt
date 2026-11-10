package com.latifi.wallet

import android.app.Application
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.data.wallet.ReownBootstrap
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LatiFiApp : Application() {
    @Inject
    lateinit var config: AppConfig

    override fun onCreate() {
        super.onCreate()
        // Hilt has injected the config by now. The wallet SDK starts here, not in an
        // activity, so a late wallet response is never lost after the process restarts.
        ReownBootstrap.init(this, config)
    }
}
