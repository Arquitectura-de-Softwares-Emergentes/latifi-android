package com.latifi.wallet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.data.fake.FakeAuthRepository
import com.latifi.wallet.data.fake.FakeChainReadRepository
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.language.AppCompatLanguageController
import com.latifi.wallet.data.prefs.DataStorePrefsStore
import com.latifi.wallet.data.prefs.PREFS_FILE_NAME
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.auth.AuthRepository
import com.latifi.wallet.domain.auth.DefaultReauthCoordinator
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.language.LanguageController
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.rates.ExchangeRateRepository
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.session.SignOutHook
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.ui.overlay.OverlayController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFS_FILE_NAME)

/**
 * Binds the wallet, auth, profile, loan, exchange rate, session, language and preferences interfaces. Demo mode (the
 * only mode that works until plans 05-13 and 05-14 add the live binding) uses the fake wallet
 * and fake API.
 *
 * The module is abstract so it can hold the `@Multibinds` declaration of the sign-out hooks.
 * Plans 05-09, 05-11 and 05-12 add a hook with `@Binds @IntoSet` in their own module and never
 * edit the use case or this declaration.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    /** The set of sign-out hooks starts empty so [com.latifi.wallet.domain.session.SignOutUseCase] always resolves. */
    @Multibinds
    abstract fun signOutHooks(): Set<SignOutHook>

    @Binds
    abstract fun bindLanguageController(controller: AppCompatLanguageController): LanguageController

    companion object {
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
        fun provideLoanRepository(config: AppConfig, clock: Clock): LoanRepository = when (config.mode) {
            AppMode.Demo -> FakeLoanRepository(clock)
            AppMode.Live -> error("The live loan repository is added by plan 05-13. Build without a project id to use demo mode.")
        }

        @Provides
        @Singleton
        fun provideChainReadRepository(config: AppConfig, loans: LoanRepository): ChainReadRepository = when (config.mode) {
            // The fake follows the loans the fake API serves, so the next installment matches the loan list.
            AppMode.Demo -> FakeChainReadRepository(loans = { (loans as? FakeLoanRepository)?.loans.orEmpty() })
            AppMode.Live -> error("The live chain reads are added by plan 05-11. Build without a project id to use demo mode.")
        }

        @Provides
        @Singleton
        fun provideExchangeRateRepository(config: AppConfig, clock: Clock): ExchangeRateRepository = when (config.mode) {
            AppMode.Demo -> FakeExchangeRateRepository(clock)
            AppMode.Live -> error("The live exchange rate repository is added by plan 05-13. Build without a project id to use demo mode.")
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
}
