package com.latifi.wallet.domain.rates

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LocalValueProviderTest {
    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val start = Instant.parse("2026-11-10T12:00:00Z")
    private val clock = MovableClock(start)
    private val repo = FakeExchangeRateRepository(clock)
    private val provider = LocalValueProvider(repo, clock)

    private val amount = TokenAmount.ofTokens(250)

    @Test
    fun rightAfterCreationTheStateIsLoadingAndNoValueExists() {
        assertEquals(RatesState.Loading, provider.rates.value)
        assertEquals(LocalValue.Loading, provider.localValue(amount, LocalCurrency.PEN))
    }

    @Test
    fun manyConversionsCostOneRepositoryCall() = runTest {
        repeat(5) { provider.ensure() }
        repeat(50) { provider.localValue(TokenAmount.ofTokens(it.toLong() + 1), LocalCurrency.PEN) }

        assertEquals(1, repo.ratesCalls)
    }

    @Test
    fun theValueUsesHalfEvenRoundingOfTheQuote() = runTest {
        provider.ensure()

        val value = provider.localValue(amount, LocalCurrency.PEN) as LocalValue.Value
        assertEquals(BigDecimal("937.50"), value.amount)
        assertEquals(LocalCurrency.PEN, value.currency)
        // 0.000001 tUSD at 3.75 is 0.00000375, which rounds to 0.00 with two fraction digits.
        val tiny = provider.localValue(TokenAmount(java.math.BigInteger.ONE), LocalCurrency.PEN) as LocalValue.Value
        assertEquals(BigDecimal("0.00"), tiny.amount)
        // CLP has no fraction digits: 250 tUSD at 950 is 237500.
        val clp = provider.localValue(amount, LocalCurrency.CLP) as LocalValue.Value
        assertEquals(BigDecimal("237500"), clp.amount)
    }

    @Test
    fun theCacheExpiresAfterFiveMinutesOnTheInjectedClock() = runTest {
        provider.ensure()
        clock.now = start.plus(Duration.ofMinutes(4)).plusSeconds(59)
        provider.ensure()
        assertEquals(1, repo.ratesCalls)

        clock.now = start.plus(Duration.ofMinutes(5))
        provider.ensure()
        assertEquals(2, repo.ratesCalls)
    }

    @Test
    fun refreshSkipsTheCache() = runTest {
        provider.ensure()
        provider.refresh()
        provider.refresh()

        assertEquals(3, repo.ratesCalls)
    }

    @Test
    fun staleAndItsTimePassThrough() = runTest {
        repo.stale = true
        provider.ensure()

        val value = provider.localValue(amount, LocalCurrency.PEN) as LocalValue.Value
        assertTrue(value.stale)
        assertEquals(start.minus(Duration.ofHours(3)), value.asOf)
    }

    @Test
    fun aFreshQuoteIsNotStale() = runTest {
        provider.ensure()

        assertEquals(false, (provider.localValue(amount, LocalCurrency.PEN) as LocalValue.Value).stale)
    }

    @Test
    fun aServiceOutageGivesUnavailableAndTheTusdTextIsUntouched() = runTest {
        repo.unavailable = true
        provider.ensure()

        assertEquals(RatesState.Unavailable, provider.rates.value)
        assertEquals(LocalValue.Unavailable, provider.localValue(amount, LocalCurrency.PEN))
        assertEquals("250.00 tUSD", amount.format(java.util.Locale.US))
    }

    @Test
    fun anOutageIsNotRetriedWithinThirtySecondsAndIsAfterwards() = runTest {
        repo.unavailable = true
        provider.ensure()
        clock.now = start.plusSeconds(29)
        provider.ensure()
        assertEquals(1, repo.ratesCalls)

        repo.unavailable = false
        clock.now = start.plusSeconds(30)
        provider.ensure()
        assertEquals(2, repo.ratesCalls)
        assertTrue(provider.rates.value is RatesState.Ready)
    }

    @Test
    fun aFailedRefreshReplacesTheOldRatesWithUnavailable() = runTest {
        provider.ensure()
        repo.unavailable = true
        provider.refresh()

        assertEquals(LocalValue.Unavailable, provider.localValue(amount, LocalCurrency.PEN))
    }

    @Test
    fun aCurrencyWithoutAQuoteIsUnavailable() = runTest {
        val partial = FakeExchangeRateRepository(clock, mapOf(LocalCurrency.PEN to BigDecimal("3.75")))
        val limited = LocalValueProvider(partial, clock)
        limited.ensure()

        assertEquals(LocalValue.Unavailable, limited.localValue(amount, LocalCurrency.COP))
        assertTrue(limited.localValue(amount, LocalCurrency.PEN) is LocalValue.Value)
    }

    @Test
    fun theStateFunctionIsPureOverTheGivenState() {
        assertEquals(LocalValue.Loading, LocalValueProvider.valueFrom(RatesState.Loading, amount, LocalCurrency.PEN))
        assertEquals(LocalValue.Unavailable, LocalValueProvider.valueFrom(RatesState.Unavailable, amount, LocalCurrency.PEN))
    }
}
