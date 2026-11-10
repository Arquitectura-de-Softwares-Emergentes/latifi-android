package com.latifi.wallet.ui.feed

import androidx.lifecycle.SavedStateHandle
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS
    private val rateRepo = FakeExchangeRateRepository(clock)

    private fun TestScope.viewModel(
        repo: FakeLoanRepository = FakeLoanRepository(clock),
        handle: SavedStateHandle = SavedStateHandle(),
        profiles: FakeProfileRepository = FakeProfileRepository(clock),
    ): FeedViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = FeedViewModel(handle, repo, profiles, LocalValueProvider(rateRepo, clock), FakePrefsStore(), sessions)
        runCurrent()
        return vm
    }

    private fun FeedViewModel.items() = state.value.content as FeedContent.Items

    @Test
    fun theDefaultQueryIsOpenSortedByReputationWithTwentyPerPage() = runTest {
        val repo = FakeLoanRepository(clock)
        viewModel(repo)

        val query = repo.queries.single()
        assertEquals(LoanStatus.OPEN, query.status)
        assertEquals(LoanSort.Reputation, query.sort)
        assertEquals(20, query.size)
        assertEquals(0, query.page)
        assertNull(query.borrower)
        assertNull(query.minLevel)
    }

    @Test
    fun theCardsComeHighestReputationFirstAndTheCounterIsTheTotal() = runTest {
        val vm = viewModel()

        assertEquals(listOf(1L, 2L, 12L, 10L, 4L, 6L), vm.items().cards.map { it.loanId.toLong() })
        assertEquals(6L, vm.state.value.counter)
    }

    @Test
    fun theCounterIsTotalItemsAndNotThePageSize() = runTest {
        val many = (1L..25L).map {
            FakeData.loan(clock, it, FakeData.LUCIA, null, 100, 1000, 30, 2, LoanStatus.OPEN, requestedDaysAgo = it)
        }
        val vm = viewModel(FakeLoanRepository(many))

        assertEquals(20, vm.items().cards.size)
        assertEquals(25L, vm.state.value.counter)
        assertTrue(vm.items().hasMore)
    }

    @Test
    fun aCardCarriesAmountReputationRateTermAndInstallments() = runTest {
        val card = viewModel().items().cards.first()

        assertEquals(BigInteger.ONE, card.loanId)
        assertEquals(TokenAmount.ofTokens(500), card.amount)
        assertEquals(ReputationLevel.EXCELLENT, card.level)
        assertEquals(820, card.score)
        assertEquals(700, card.rateBps)
        assertEquals(60, card.termDays)
        assertEquals(6, card.installments)
        assertFalse(card.isOwn)
    }

    @Test
    fun theNameIsNullUntilTheProfileAnswersAndThenFillsIn() = runTest {
        val vm = viewModel()
        runCurrent()

        val byId = vm.items().cards.associateBy { it.loanId.toLong() }
        assertEquals("Lucia Fernandez", byId.getValue(1L).name)
        assertEquals("Mateo Quispe", byId.getValue(2L).name)
        // The new borrower has no public profile, so the card keeps the fallback.
        assertNull(byId.getValue(6L).name)
    }

    @Test
    fun theOwnRequestIsMarked() = runTest {
        val own = viewModel().items().cards.single { it.loanId == BigInteger.valueOf(12) }

        assertTrue(own.isOwn)
        assertEquals(TokenAmount.ofTokens(120), own.amount)
    }
}
