package com.latifi.wallet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.core.AppMode
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
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFS_FILE_NAME)
private val Context.recoveryDataStore: DataStore<Preferences> by preferencesDataStore(name = RECOVERY_FILE_NAME)

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

        @Provides
        @Singleton
        fun provideWalletGateway(config: AppConfig): WalletGateway = when (config.mode) {
            AppMode.Demo -> FakeWalletGateway(txHashOf = { id -> "0x" + id.toString(16).padStart(HASH_DIGITS, '0') })
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
        fun provideReputationRepository(config: AppConfig, clock: Clock): ReputationRepository = when (config.mode) {
            AppMode.Demo -> FakeReputationRepository(clock)
            AppMode.Live -> error("The live reputation repository is added by plan 05-13. Build without a project id to use demo mode.")
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

        @Provides
        @Singleton
        fun provideRpcClient(config: AppConfig): RpcClient = when (config.mode) {
            AppMode.Demo -> FakeRpcClient()
            AppMode.Live -> error("The live JSON-RPC client is added by plan 05-13. Build without a project id to use demo mode.")
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

        /** The application calls `resume()` once the wallet delegate is set (plan 05-14). */
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
