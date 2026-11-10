package com.latifi.wallet.data.fake

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.InstallmentSchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.ZoneOffset

class FakeChainReadRepositoryTest {
    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val loans = FakeData.loans(clock)
    private val repo = FakeChainReadRepository { loans }

    @Test
    fun theDemoWalletHasOneThousandTokens() = runTest {
        assertEquals(TokenAmount.ofTokens(1_000), repo.tokenBalance(FakeData.DEMO_ADDRESS).getOrThrow())
    }

    @Test
    fun addressesCompareWithoutCase() = runTest {
        repo.setTokenBalance("0xABCDEF0000000000000000000000000000000001", TokenAmount.ofTokens(7))

        val read = repo.tokenBalance("0xabcdef0000000000000000000000000000000001").getOrThrow()

        assertEquals(TokenAmount.ofTokens(7), read)
    }

    @Test
    fun anUnknownAddressHasNothing() = runTest {
        assertEquals(TokenAmount(BigInteger.ZERO), repo.tokenBalance(FakeData.LUCIA).getOrThrow())
        assertEquals(BigInteger.ZERO, repo.polBalance(FakeData.LUCIA).getOrThrow())
        assertEquals(BigInteger.ZERO, repo.allowance(FakeData.LUCIA, FakeData.LENDER_A).getOrThrow())
    }

    @Test
    fun anAllowanceIsPerOwnerAndSpender() = runTest {
        repo.setAllowance(FakeData.DEMO_ADDRESS, FakeData.LENDER_A, BigInteger.TEN)

        assertEquals(BigInteger.TEN, repo.allowance(FakeData.DEMO_ADDRESS, FakeData.LENDER_A).getOrThrow())
        assertEquals(BigInteger.ZERO, repo.allowance(FakeData.DEMO_ADDRESS, FakeData.LENDER_B).getOrThrow())
    }

    @Test
    fun aQueuedFailureHitsTheNextCallOfThatReadOnly() = runTest {
        repo.failNext(FakeChainReadRepository.Read.PolBalance, AppError.Offline)

        val failed = repo.polBalance(FakeData.DEMO_ADDRESS)
        val other = repo.tokenBalance(FakeData.DEMO_ADDRESS)
        val again = repo.polBalance(FakeData.DEMO_ADDRESS)

        assertEquals(AppError.Offline, (failed.exceptionOrNull() as AppException).error)
        assertTrue(other.isSuccess)
        assertTrue(again.isSuccess)
    }

    @Test
    fun theNextInstallmentOfAPartiallyPaidLoanIsTheUnpaidPartOfTheCurrentOne() = runTest {
        val loan = loans.first { it.loanId == BigInteger.valueOf(11) }

        val next = repo.nextInstallment(loan.loanId).getOrThrow()

        assertEquals(2, next.index)
        assertEquals(loan.nextDueDate, next.dueAt)
        assertEquals(TokenAmount(InstallmentSchedule.remainingOnCurrent(loan)), next.unpaid)
    }

    @Test
    fun aLoanThatIsNotActiveHasNoNextInstallment() = runTest {
        val open = repo.nextInstallment(BigInteger.valueOf(12))
        val missing = repo.nextInstallment(BigInteger.valueOf(999))

        assertEquals(AppError.NotFound, (open.exceptionOrNull() as AppException).error)
        assertEquals(AppError.NotFound, (missing.exceptionOrNull() as AppException).error)
    }

    @Test
    fun everyReadIsLoggedWithItsLowercaseAddress() = runTest {
        repo.tokenBalance("0xABC")
        repo.polBalance("0xDEF")

        assertEquals(
            listOf(
                FakeChainReadRepository.Read.TokenBalance to "0xabc",
                FakeChainReadRepository.Read.PolBalance to "0xdef",
            ),
            repo.reads,
        )
    }
}
