package com.latifi.wallet

import android.app.Application
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LatiFiApp : Application() {
    // Lazy, so nothing wallet related is built before the start-up order in AppStartup runs.
    @Inject
    lateinit var startup: Lazy<AppStartup>

    override fun onCreate() {
        super.onCreate()
        // Hilt has injected by now. The wallet SDK, its delegate and the resumer start here, not in
        // an activity, so a late wallet answer is never lost after the process restarts.
        startup.get().run()
    }
}
