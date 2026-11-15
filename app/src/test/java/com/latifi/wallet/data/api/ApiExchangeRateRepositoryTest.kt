package com.latifi.wallet.data.api

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ApiExchangeRateRepositoryTest {
    private val rig = ApiTestRig()
    private val repository = ApiExchangeRateRepository(rig.api)
    private val fixture = ApiClientFactory.json.parseToJsonElement(ApiTestRig.fixture("rates.json")).jsonObject

    @After
    fun tearDown() = rig.close()

    @Test
    fun ratesParsesTheBareArrayAndTheFreshFlag() = runBlocking {
        rig.enqueueJson(fixture.getValue("fresh").toString())

        val snapshot = repository.rates().getOrThrow()

        assertEquals("/api/v1/exchange-rate/rates", rig.server.takeRequest().path)
        assertFalse(snapshot.stale)
        assertEquals(BigDecimal("3.7512"), snapshot.quoteFor(LocalCurrency.PEN)?.rate)
    }

    @Test
    fun aStaleQuoteMakesTheWholeSnapshotStaleAndKeepsTheOldestTime() = runBlocking {
        rig.enqueueJson(fixture.getValue("stale").toString())

        val snapshot = repository.rates().getOrThrow()

        assertTrue(snapshot.stale)
        assertEquals(Instant.parse("2026-01-01T09:00:00Z"), snapshot.asOf)
    }

    @Test
    fun noRateAtAllIsRateUnavailable() = runBlocking {
        rig.enqueueError(503, "RATE_UNAVAILABLE")

        val failure = repository.rates().exceptionOrNull() as AppException

        assertEquals(AppError.RateUnavailable, failure.error)
    }

    @Test
    fun anEmptyListReadsAsRateUnavailable() = runBlocking {
        rig.enqueueJson("[]")

        val failure = repository.rates().exceptionOrNull() as AppException

        assertEquals(AppError.RateUnavailable, failure.error)
    }

    @Test
    fun convertSendsTheBaseUnitsAndTheCurrencyCode() = runBlocking {
        rig.enqueueJson(fixture.getValue("convert").toString())

        val converted = repository.convert(TokenAmount.ofTokens(250), LocalCurrency.PEN).getOrThrow()

        val url = rig.server.takeRequest().requestUrl!!
        assertEquals("/api/v1/exchange-rate/convert", url.encodedPath)
        assertEquals("250000000", url.queryParameter("amount"))
        assertEquals("PEN", url.queryParameter("currency"))
        assertEquals(BigDecimal("937.80"), converted.value)
        assertEquals(BigDecimal("3.7512"), converted.rate)
        assertFalse(converted.stale)
    }

    @Test
    fun convertWithoutAnyRateIsRateUnavailable() = runBlocking {
        rig.enqueueError(503, "RATE_UNAVAILABLE")

        val failure = repository.convert(TokenAmount.ofTokens(1), LocalCurrency.CLP).exceptionOrNull() as AppException

        assertEquals(AppError.RateUnavailable, failure.error)
    }
}
