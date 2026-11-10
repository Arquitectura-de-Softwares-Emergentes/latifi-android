package com.latifi.wallet.ui.loans

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.UiLoanStatus
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.ui.common.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LoansViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val address = FakeWalletGateway.DEMO_ADDRESS

    private fun TestScope.viewModel(repo: FakeLoanRepository = FakeLoanRepository(clock)): LoansViewModel {
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session(address, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val vm = LoansViewModel(repo, LocalValueProvider(FakeExchangeRateRepository(clock), clock), FakePrefsStore(), sessions)
        runCurrent()
        return vm
    }

    @Test
    fun theQueryHasTheBorrowerSortRecentAndSize20() = runTest {
        val repo = FakeLoanRepository(clock)
        viewModel(repo)

        val query = repo.queries.single()
        assertEquals(address, query.borrower)
        assertEquals(LoanSort.Recent, query.sort)
        assertEquals(20, query.size)
        assertEquals(0, query.page)
        assertEquals(null, query.status)
    }

    @Test
    fun oneLoanMapsToTheExpectedCardModel() = runTest {
        val loan = FakeData.loan(
            clock, 11, FakeData.DEMO_ADDRESS, FakeData.LENDER_A, 250, 1000, 30, 3, LoanStatus.ACTIVE, paid = 1,
            requestedDaysAgo = 13, fundedDaysAgo = 12,
        )
        val vm = viewModel(FakeLoanRepository(listOf(loan)))

        val card = (vm.state.value.content as Loadable.Success).data.single()
        assertEquals(BigInteger.valueOf(11), card.loanId)
        assertEquals(TokenAmount.ofTokens(250), card.amount)
        assertEquals(UiLoanStatus.Pending, card.status)
        assertEquals(1000, card.rateBps)
        assertEquals(30, card.termDays)
        assertEquals(3, card.installments)
        assertEquals(1, card.installmentsPaid)
        assertEquals(loan.nextDueDate, card.nextDue)
    }
}
