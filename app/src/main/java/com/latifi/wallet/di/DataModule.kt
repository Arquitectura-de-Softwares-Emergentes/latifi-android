package com.latifi.wallet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.data.fake.FakeAuthRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.prefs.DataStorePrefsStore
import com.latifi.wallet.data.prefs.PREFS_FILE_NAME
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.auth.AuthRepository
import com.latifi.wallet.domain.auth.DefaultReauthCoordinator
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.ui.overlay.OverlayController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFS_FILE_NAME)

/**
 * Binds the wallet, auth, session and preferences interfaces. Demo mode (the only mode that
 * works until plans 05-13 and 05-14 add the live binding) uses the fake wallet and fake API.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideWalletGateway(config: AppConfig): WalletGateway = when (config.mode) {
        AppMode.Demo -> FakeWalletGateway()
        AppMode.Live -> error("The live wallet gateway is added by plan 05-14. Build without a project id to use demo mode.")
    }

    @Provides
    @Singleton
    fun provideAuthRepository(config: AppConfig, clock: Clock): AuthRepository = when (config.mode) {
        AppMode.Demo -> FakeAuthRepository(clock)
        AppMode.Live -> error("The live auth repository is added by plan 05-13. Build without a project id to use demo mode.")
    }

    @Provides
    @Singleton
    fun provideProfileRepository(config: AppConfig, clock: Clock): ProfileRepository = when (config.mode) {
        AppMode.Demo -> FakeProfileRepository(clock)
        AppMode.Live -> error("The live profile repository is added by plan 05-13. Build without a project id to use demo mode.")
    }

    @Provides
    @Singleton
    fun provideSessionStore(): SessionStore = InMemorySessionStore()

    @Provides
    @Singleton
    fun providePrefsStore(@ApplicationContext context: Context): PrefsStore =
        DataStorePrefsStore(context.prefsDataStore)

    @Provides
    @Singleton
    fun provideReauthCoordinator(overlays: OverlayController): ReauthCoordinator =
        DefaultReauthCoordinator(overlays)
}
