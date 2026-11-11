package com.latifi.wallet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.data.api.ApiAuthRepository
import com.latifi.wallet.data.api.ApiClientFactory
import com.latifi.wallet.data.api.ApiExchangeRateRepository
import com.latifi.wallet.data.api.ApiLoanRepository
import com.latifi.wallet.data.api.ApiProfileRepository
import com.latifi.wallet.data.api.ApiReputationRepository
import com.latifi.wallet.data.api.LatiFiApi
import com.latifi.wallet.data.chain.OkHttpRpcClient
import com.latifi.wallet.data.chain.RpcChainReadRepository
import com.latifi.wallet.data.fake.FakeAuthRepository
import com.latifi.wallet.data.fake.FakeChainReadRepository
import com.latifi.wallet.data.fake.FakeChainSimulator
import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeReputationRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.language.AppCompatLanguageController
import com.latifi.wallet.data.prefs.DataStorePrefsStore
import com.latifi.wallet.data.prefs.PREFS_FILE_NAME
import com.latifi.wallet.data.fake.FakeRpcClient
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.data.session.KeystoreEnvelope
import com.latifi.wallet.data.session.KeystoreSessionStore
import com.latifi.wallet.data.session.SESSION_FILE_NAME
import com.latifi.wallet.data.wallet.AppKitWalletGateway
import com.latifi.wallet.data.wallet.UnconfiguredWalletGateway
import com.latifi.wallet.data.tx.DataStoreRecoveryStore
import com.latifi.wallet.data.tx.RECOVERY_FILE_NAME
import com.latifi.wallet.domain.auth.AuthRepository
import com.latifi.wallet.domain.auth.DefaultReauthCoordinator
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.chain.ReceiptPoller
import com.latifi.wallet.domain.chain.RpcClient
import com.latifi.wallet.domain.language.LanguageController
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.reputation.ReputationRepository
import com.latifi.wallet.domain.rates.ExchangeRateRepository
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.session.SignOutHook
import com.latifi.wallet.domain.tx.ChainReconciler
import com.latifi.wallet.domain.tx.IndexerLagWaiter
import com.latifi.wallet.domain.tx.Preflight
import com.latifi.wallet.domain.tx.Reconciler
import com.latifi.wallet.domain.tx.RecoveryStore
import com.latifi.wallet.domain.tx.TransactionCoordinator
import com.latifi.wallet.domain.tx.TransactionResumer
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.ui.overlay.OverlayController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFS_FILE_NAME)
private val Context.recoveryDataStore: DataStore<Preferences> by preferencesDataStore(name = RECOVERY_FILE_NAME)
// A session file that is corrupt is replaced by an empty one: the person signs in again instead of the app failing at every start.
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SESSION_FILE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Binds the wallet, auth, profile, loan, reputation, exchange rate, chain read, session, language and
 * preferences interfaces. [AppMode.Demo] binds the fakes. [AppMode.Live] binds the real API and
 * JSON-RPC implementations of plan 05-13, which reach the [LatiFiApi] through a [Provider] so Demo
 * mode never builds the HTTP stack. Live also binds the AppKit wallet gateway and the session store
 * sealed with the Android Keystore (plan 05-14), both reached through a [Provider] so Demo never
 * builds them either.
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

    /** The in-flight transaction record is cleared on Disconnect like every other hook. */
    @Binds
    @IntoSet
    abstract fun bindRecoverySignOutHook(store: DataStoreRecoveryStore): SignOutHook

    /** The coordinator forgets its run and closes the sheet on Disconnect. */
    @Binds
    @IntoSet
    abstract fun bindTransactionSignOutHook(coordinator: TransactionCoordinator): SignOutHook

    @Binds
    abstract fun bindRecoveryStore(store: DataStoreRecoveryStore): RecoveryStore

    @Binds
    abstract fun bindLanguageController(controller: AppCompatLanguageController): LanguageController

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()

        /**
         * Demo binds the fake wallet. Live binds AppKit when the build has a real project id, which
         * Live always has, and the unconfigured gateway for the case that cannot happen but would
         * otherwise reach a wallet SDK that never started. The AppKit gateway is built on first use.
         */
        @Provides
        @Singleton
        fun provideWalletGateway(config: AppConfig, appKit: Provider<AppKitWalletGateway>): WalletGateway = when {
            config.mode == AppMode.Demo ->
                FakeWalletGateway(txHashOf = { id -> "0x" + id.toString(16).padStart(HASH_DIGITS, '0') })
            config.isWalletConfigured -> appKit.get()
            else -> UnconfiguredWalletGateway()
        }

        @Provides
        @Singleton
        fun provideAuthRepository(config: AppConfig, clock: Clock, api: Provider<LatiFiApi>): AuthRepository = when (config.mode) {
            AppMode.Demo -> FakeAuthRepository(clock)
            AppMode.Live -> ApiAuthRepository(api.get())
        }

        @Provides
        @Singleton
        fun provideProfileRepository(config: AppConfig, clock: Clock, api: Provider<LatiFiApi>): ProfileRepository = when (config.mode) {
            AppMode.Demo -> FakeProfileRepository(clock)
            AppMode.Live -> ApiProfileRepository(api.get())
        }

        @Provides
        @Singleton
        fun provideLoanRepository(config: AppConfig, clock: Clock, api: Provider<LatiFiApi>): LoanRepository = when (config.mode) {
            AppMode.Demo -> FakeLoanRepository(clock)
            AppMode.Live -> ApiLoanRepository(api.get())
        }

        @Provides
        @Singleton
        fun provideChainReadRepository(config: AppConfig, loans: LoanRepository, rpc: RpcClient): ChainReadRepository = when (config.mode) {
            // The fake follows the loans the fake API serves, so the next installment matches the loan list.
            AppMode.Demo -> FakeChainReadRepository(loans = { (loans as? FakeLoanRepository)?.loans.orEmpty() })
            AppMode.Live -> RpcChainReadRepository(rpc)
        }

        @Provides
        @Singleton
        fun provideReputationRepository(config: AppConfig, clock: Clock, api: Provider<LatiFiApi>): ReputationRepository = when (config.mode) {
            AppMode.Demo -> FakeReputationRepository(clock)
            AppMode.Live -> ApiReputationRepository(api.get())
        }

        @Provides
        @Singleton
        fun provideExchangeRateRepository(config: AppConfig, clock: Clock, api: Provider<LatiFiApi>): ExchangeRateRepository = when (config.mode) {
            AppMode.Demo -> FakeExchangeRateRepository(clock)
            AppMode.Live -> ApiExchangeRateRepository(api.get())
        }

        /**
         * Demo keeps the session in memory, so a restart signs in again. Live seals the address, the
         * token and the expiry with an AES-256-GCM key of the Android Keystore and keeps only the
         * ciphertext in a DataStore file, so the session survives a process kill.
         */
        @Provides
        @Singleton
        fun provideSessionStore(config: AppConfig, @ApplicationContext context: Context): SessionStore = when (config.mode) {
            AppMode.Demo -> InMemorySessionStore()
            AppMode.Live -> KeystoreSessionStore(KeystoreEnvelope(), context.sessionDataStore)
        }

        @Provides
        @Singleton
        fun providePrefsStore(@ApplicationContext context: Context): PrefsStore =
            DataStorePrefsStore(context.prefsDataStore)

        @Provides
        @Singleton
        fun provideReauthCoordinator(overlays: OverlayController): ReauthCoordinator =
            DefaultReauthCoordinator(overlays)

        @Provides
        @Singleton
        fun provideRpcClient(config: AppConfig): RpcClient = when (config.mode) {
            AppMode.Demo -> FakeRpcClient()
            AppMode.Live -> OkHttpRpcClient(config.amoyRpcUrl, ApiClientFactory.rpcHttpClient(), ApiClientFactory.json)
        }

        /** The 30 second wait for the indexer after a confirmed transaction. */
        @Provides
        @Singleton
        fun provideIndexerLagWaiter(): IndexerLagWaiter = IndexerLagWaiter()

        @Provides
        @Singleton
        fun provideReceiptPoller(rpc: RpcClient): ReceiptPoller = ReceiptPoller(rpc)

        @Provides
        @Singleton
        fun provideDataStoreRecoveryStore(@ApplicationContext context: Context): DataStoreRecoveryStore =
            DataStoreRecoveryStore(context.recoveryDataStore)

        @Provides
        @Singleton
        fun provideTransactionCoordinator(
            wallet: WalletGateway,
            rpc: RpcClient,
            poller: ReceiptPoller,
            recovery: RecoveryStore,
            overlays: OverlayController,
            clock: Clock,
            chain: ChainReadRepository,
            loans: LoanRepository,
            reconciler: Reconciler,
            waiter: IndexerLagWaiter,
        ): TransactionCoordinator {
            wireDemoChain(wallet, rpc, chain, loans, clock)
            return TransactionCoordinator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            wallet = wallet,
            preflight = Preflight(rpc),
            poller = poller,
            recovery = recovery,
            overlays = overlays,
            nowMillis = clock::millis,
            reconciler = reconciler,
            lagWaiter = waiter,
            )
        }

        /**
         * In Demo mode an approved transaction changes the fake chain: the simulator applies the
         * calldata and the fake node answers the receipt. Live mode has none of these fakes, so
         * nothing is wired and the simulator never exists outside Demo.
         */
        private fun wireDemoChain(
            wallet: WalletGateway,
            rpc: RpcClient,
            chain: ChainReadRepository,
            loans: LoanRepository,
            clock: Clock,
        ) {
            if (wallet !is FakeWalletGateway || rpc !is FakeRpcClient ||
                chain !is FakeChainReadRepository || loans !is FakeLoanRepository
            ) {
                return
            }
            val simulator = FakeChainSimulator(chain, loans, clock)
            wallet.onSend = { hash, request ->
                val result = simulator.apply(request.from, request.to, request.data)
                rpc.script(hash, FakeReceiptScript(nullPolls = DEMO_NULL_POLLS, status = result.status, logs = result.logs))
            }
        }

        private const val HASH_DIGITS = 64
        private const val DEMO_NULL_POLLS = 2

        @Provides
        @Singleton
        fun provideReconciler(
            chain: ChainReadRepository,
            loans: LoanRepository,
            sessions: SessionStore,
        ): Reconciler = ChainReconciler(chain, loans, sessions)

        /** [com.latifi.wallet.AppStartup] calls `resume()` once the wallet delegate is set. */
        @Provides
        @Singleton
        fun provideTransactionResumer(
            recovery: RecoveryStore,
            coordinator: TransactionCoordinator,
            reconciler: Reconciler,
            clock: Clock,
        ): TransactionResumer = TransactionResumer(
            store = recovery,
            coordinator = coordinator,
            reconciler = reconciler,
            waiter = IndexerLagWaiter(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            nowMillis = clock::millis,
        )
    }
}
