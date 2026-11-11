package com.latifi.wallet.data.api

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.Instant

class ApiLoanRepositoryTest {
    private val rig = ApiTestRig()
    private val repository = ApiLoanRepository(rig.api)

    @After
    fun tearDown() = rig.close()

    @Test
    fun theDefaultFeedQueryReachesTheServerWithExactlyTheFourParameters() = runBlocking {
        rig.enqueueFixture("loan-page.json")

        repository.list(LoanQuery(status = LoanStatus.OPEN)).getOrThrow()

        val request = rig.server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/loans?status=OPEN&sort=reputation&page=0&size=20", request.path)
    }

    @Test
    fun aCombinedQuerySendsExactlyItsParameters() = runBlocking {
        rig.enqueueFixture("loan-page.json")
        val query = LoanQuery(
            status = LoanStatus.OPEN,
            minAmount = BigInteger("100000000"),
            maxAmount = BigInteger("500000000"),
            maxTermDays = 30,
            minLevel = ReputationLevel.TRUSTED,
            borrower = "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD",
            sort = LoanSort.NextDue,
            page = 2,
            size = 50,
        )

        repository.list(query).getOrThrow()

        val url = rig.server.takeRequest().requestUrl!!
        assertEquals("OPEN", url.queryParameter("status"))
        assertEquals("100000000", url.queryParameter("minAmount"))
        assertEquals("500000000", url.queryParameter("maxAmount"))
        assertEquals("30", url.queryParameter("maxTermDays"))
        assertEquals("TRUSTED", url.queryParameter("minLevel"))
        assertEquals("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd", url.queryParameter("borrower"))
        assertEquals("nextDue", url.queryParameter("sort"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("50", url.queryParameter("size"))
        assertNull(url.queryParameter("lender"))
    }

    @Test
    fun aLenderPortfolioQuerySendsTheLenderAddressOnly() = runBlocking {
        rig.enqueueFixture("loan-page.json")

        repository.list(LoanQuery(lender = "0x3333333333333333333333333333333333333333", sort = LoanSort.NextDue)).getOrThrow()

        val url = rig.server.takeRequest().requestUrl!!
        assertEquals(setOf("lender", "sort", "page", "size"), url.queryParameterNames)
    }

    @Test
    fun aSeventyEightDigitValueSurvivesAsABigInteger() = runBlocking {
        rig.enqueueFixture("loan-page.json")

        val page = repository.list(LoanQuery()).getOrThrow()

        val big = page.items[2]
        assertEquals(BigInteger("123456789012345678901234567890123456789012345678901234567890123456789012345678"), big.principal)
        assertEquals(big.principal, big.loanId)
        assertEquals(78, big.principal.toString().length)
    }

    @Test
    fun nullableFieldsMapToNullAndTimestampsBecomeInstants() = runBlocking {
        rig.enqueueFixture("loan-page.json")

        val page = repository.list(LoanQuery()).getOrThrow()

        val open = page.items[0]
        assertNull(open.lender)
        assertNull(open.fundedAt)
        assertNull(open.finalDueDate)
        assertNull(open.nextDueDate)
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), open.requestedAt)
        assertEquals(LoanStatus.OPEN, open.status)
        assertEquals(ReputationLevel.TRUSTED, open.borrowerReputation.level)
        val active = page.items[1]
        assertEquals("0x3333333333333333333333333333333333333333", active.lender)
        assertEquals(Instant.parse("2026-02-01T12:00:00Z"), active.nextDueDate)
        assertEquals(Instant.parse("2026-03-03T12:00:00Z"), active.finalDueDate)
        assertEquals(Instant.parse("2026-01-03T08:30:00.250Z"), page.items[2].requestedAt)
    }

    @Test
    fun totalItemsFeedsTheCounterAndAddressesAreLowercase() = runBlocking {
        rig.enqueueJson(
            ApiTestRig.fixture("loan-page.json").replace("0x1111111111111111111111111111111111111111", "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
        )

        val page = repository.list(LoanQuery()).getOrThrow()

        assertEquals(42L, page.totalItems)
        assertEquals(3, page.items.size)
        assertTrue(page.hasMore)
        assertEquals("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", page.items[0].borrower)
    }

    @Test
    fun anItemWithAnUnknownStatusIsSkippedWithoutFailingThePage() = runBlocking {
        rig.enqueueJson(ApiTestRig.fixture("loan-page.json").replaceFirst("\"status\": \"ACTIVE\"", "\"status\": \"CANCELLED\""))

        val page = repository.list(LoanQuery()).getOrThrow()

        assertEquals(2, page.items.size)
        assertEquals(listOf(BigInteger.valueOf(7)), page.items.take(1).map { it.loanId })
        assertTrue(page.items.none { it.loanId == BigInteger.valueOf(8) })
        assertEquals(42L, page.totalItems)
    }

    @Test
    fun anUnknownLevelReadsAsNew() = runBlocking {
        rig.enqueueJson(ApiTestRig.fixture("loan-page.json").replaceFirst("\"EXCELLENT\"", "\"LEGENDARY\""))

        val page = repository.list(LoanQuery()).getOrThrow()

        assertEquals(ReputationLevel.NEW, page.items[1].borrowerReputation.level)
    }

    @Test
    fun getReadsOneLoanByItsDecimalId() = runBlocking {
        rig.enqueueJson(ApiTestRig.fixture("loan-page.json").let { text ->
            // The detail answer is a LoanSummary, so take the first item of the page.
            val start = text.indexOf("{", text.indexOf("\"items\""))
            val end = text.indexOf("},\n    {", start)
            text.substring(start, end + 1)
        })

        val loan = repository.get(BigInteger.valueOf(7)).getOrThrow()

        assertEquals("/api/v1/loans/7", rig.server.takeRequest().path)
        assertEquals(BigInteger.valueOf(7), loan.loanId)
    }

    @Test
    fun aMissingLoanFailsWithNotFound() = runBlocking {
        rig.enqueueError(404, "LOAN_NOT_FOUND")

        val failure = repository.get(BigInteger.valueOf(99)).exceptionOrNull() as AppException

        assertEquals(AppError.NotFound, failure.error)
    }

    @Test
    fun aServerFailureBecomesAnAppErrorAndNeverTheServerText() = runBlocking {
        rig.enqueueError(500, "INTERNAL_ERROR")

        val failure = repository.list(LoanQuery()).exceptionOrNull() as AppException

        assertEquals(AppError.Server, failure.error)
        assertTrue(!failure.message.orEmpty().contains("server text"))
    }
}
