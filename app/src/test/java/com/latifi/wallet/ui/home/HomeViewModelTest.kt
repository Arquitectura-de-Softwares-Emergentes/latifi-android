package com.latifi.wallet.ui.home

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeChainReadRepository
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS
    private val rateRepo = FakeExchangeRateRepository(clock)

    private fun TestScope.viewModel(chain: FakeChainReadRepository = FakeChainReadRepository()): HomeViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = HomeViewModel(chain, sessions, LocalValueProvider(rateRepo, clock), FakePrefsStore())
        runCurrent()
        return vm
    }

    @Test
    fun theBalanceIsLoadedForTheSessionAddress() = runTest {
        val chain = FakeChainReadRepository()
        val vm = viewModel(chain)

        assertEquals(Loadable.Success(TokenAmount.ofTokens(1_000)), vm.state.value.balance)
        val balanceReads = chain.reads.filter { it.first == FakeChainReadRepository.Read.TokenBalance }
        assertEquals(listOf(address.lowercase()), balanceReads.map { it.second })
    }

    @Test
    fun aFailingBalanceReadGivesAnErrorInThatSectionOnly() = runTest {
        val chain = FakeChainReadRepository()
        chain.failNext(FakeChainReadRepository.Read.TokenBalance, AppError.Offline)
        val vm = viewModel(chain)

        assertEquals(Loadable.Error(ErrorKind.Offline), vm.state.value.balance)
        // The rates are a different section and still arrive.
        assertEquals(true, vm.state.value.rates is com.latifi.wallet.domain.rates.RatesState.Ready)
    }

    @Test
    fun reloadingAfterAFailureShowsTheBalance() = runTest {
        val chain = FakeChainReadRepository()
        chain.failNext(FakeChainReadRepository.Read.TokenBalance, AppError.Server)
        val vm = viewModel(chain)

        vm.onEvent(HomeEvent.ReloadBalance)
        runCurrent()

        assertEquals(Loadable.Success(TokenAmount.ofTokens(1_000)), vm.state.value.balance)
    }
}
