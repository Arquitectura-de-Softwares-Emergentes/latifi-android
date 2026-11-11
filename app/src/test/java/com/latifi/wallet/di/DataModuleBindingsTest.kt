package com.latifi.wallet.di

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
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeReputationRepository
import com.latifi.wallet.data.fake.FakeRpcClient
import com.latifi.wallet.support.testConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import javax.inject.Provider

/**
 * The Live and Demo bindings of [DataModule]. Live needs a real looking project id and an API host
 * that does not end in `.invalid`. The API is reached through a provider, so Demo never builds it.
 */
class DataModuleBindingsTest {
    private val clock = Clock.systemUTC()
    private val demo = testConfig()
    private val live = demo.copy(reownProjectId = "0123456789abcdef0123456789abcdef", apiBaseUrl = "https://api.example/api/v1/")
    private var apiBuilt = 0
    private val api = Provider<LatiFiApi> {
        apiBuilt += 1
        ApiClientFactory.api(ApiClientFactory.retrofit("https://api.example/api/v1/", ApiClientFactory.rpcHttpClient()))
    }

    @Test
    fun theTestConfigsPickTheModesTheyName() {
        assertEquals(AppMode.Demo, demo.mode)
        assertEquals(AppMode.Live, live.mode)
        assertEquals(AppMode.Demo, live.copy(apiBaseUrl = "https://latifi-api.invalid/api/v1/").mode)
        assertEquals(AppMode.Demo, live.copy(reownProjectId = AppConfig.PLACEHOLDER_PROJECT_ID).mode)
    }

    @Test
    fun demoBindsTheFakesAndNeverBuildsTheApi() {
        assertTrue(DataModule.provideAuthRepository(demo, clock, api) is FakeAuthRepository)
        assertTrue(DataModule.provideProfileRepository(demo, clock, api) is FakeProfileRepository)
        val loans = DataModule.provideLoanRepository(demo, clock, api)
        assertTrue(loans is FakeLoanRepository)
        assertTrue(DataModule.provideReputationRepository(demo, clock, api) is FakeReputationRepository)
        assertTrue(DataModule.provideExchangeRateRepository(demo, clock, api) is FakeExchangeRateRepository)
        val rpc = DataModule.provideRpcClient(demo)
        assertTrue(rpc is FakeRpcClient)
        assertTrue(DataModule.provideChainReadRepository(demo, loans, rpc) is FakeChainReadRepository)
        assertEquals(0, apiBuilt)
    }

    @Test
    fun liveBindsTheRealRepositoriesAndTheJsonRpcClient() {
        assertTrue(DataModule.provideAuthRepository(live, clock, api) is ApiAuthRepository)
        assertTrue(DataModule.provideProfileRepository(live, clock, api) is ApiProfileRepository)
        val loans = DataModule.provideLoanRepository(live, clock, api)
        assertTrue(loans is ApiLoanRepository)
        assertTrue(DataModule.provideReputationRepository(live, clock, api) is ApiReputationRepository)
        assertTrue(DataModule.provideExchangeRateRepository(live, clock, api) is ApiExchangeRateRepository)
        val rpc = DataModule.provideRpcClient(live)
        assertTrue(rpc is OkHttpRpcClient)
        assertTrue(DataModule.provideChainReadRepository(live, loans, rpc) is RpcChainReadRepository)
        assertFalse(apiBuilt == 0)
    }

    @Test
    fun theLiveWalletGatewayStillNamesPlanZeroFiveFourteen() {
        val failure = runCatching { DataModule.provideWalletGateway(live) }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("05-14"))
    }
}
