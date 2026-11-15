package com.latifi.wallet.domain.rates

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.RateSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What the provider knows about rates right now. */
sealed interface RatesState {
    /** The first call has not answered yet. */
    data object Loading : RatesState

    data class Ready(val snapshot: RateSnapshot) : RatesState

    /** The provider has no rate (503 RATE_UNAVAILABLE) or the call failed. */
    data object Unavailable : RatesState
}

/** The local estimate of one tUSD amount. Display only: nothing signed or stored reads it. */
sealed interface LocalValue {
    /** [stale] and [asOf] come from the quote the value was computed with. */
    data class Value(
        val amount: BigDecimal,
        val currency: LocalCurrency,
        val stale: Boolean,
        val asOf: Instant,
    ) : LocalValue

    data object Unavailable : LocalValue

    /** Rates have not answered yet. The screens draw nothing for it, the tUSD amount is enough. */
    data object Loading : LocalValue
}

/**
 * Holds the last rates in memory and converts tUSD to a local currency on the client, with
 * `BigDecimal` half-even rounding (`LocalCurrency.convert`). One call to the rate service serves
 * every card of a list: [ensure] reads the cache for [CACHE_TTL] on the injected clock, and only
 * [refresh] (pull to refresh) skips it. A failed call counts as Unavailable for [FAILURE_TTL], so
 * a provider outage cannot turn into one request per screen.
 */
@Singleton
class LocalValueProvider @Inject constructor(
    private val repository: ExchangeRateRepository,
    private val clock: Clock,
) {
    private val mutableRates = MutableStateFlow<RatesState>(RatesState.Loading)
    private val lock = Mutex()
    private var fetchedAt: Instant? = null

    val rates: StateFlow<RatesState> = mutableRates.asStateFlow()

    /** Loads the rates when there are none, or when they are older than the cache allows. */
    suspend fun ensure() {
        lock.withLock {
            val at = fetchedAt
            val ttl = if (mutableRates.value is RatesState.Unavailable) FAILURE_TTL else CACHE_TTL
            if (at != null && clock.instant().isBefore(at.plus(ttl))) return
            load()
        }
    }

    /** Loads the rates again whatever the cache says. The old value stays on screen until the answer comes. */
    suspend fun refresh() {
        lock.withLock { load() }
    }

    /** The local estimate of [amount] with the rates held now. */
    fun localValue(amount: TokenAmount, currency: LocalCurrency): LocalValue =
        valueFrom(mutableRates.value, amount, currency)

    private suspend fun load() {
        val result = repository.rates()
        fetchedAt = clock.instant()
        mutableRates.value = result.fold(
            onSuccess = { RatesState.Ready(it) },
            onFailure = { RatesState.Unavailable },
        )
    }

    companion object {
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
        val FAILURE_TTL: Duration = Duration.ofSeconds(30)

        /**
         * The conversion as a pure function of a [RatesState], so a stateless screen can compute it
         * from the state it was given. A currency with no quote is Unavailable.
         */
        fun valueFrom(state: RatesState, amount: TokenAmount, currency: LocalCurrency): LocalValue =
            when (state) {
                RatesState.Loading -> LocalValue.Loading
                RatesState.Unavailable -> LocalValue.Unavailable
                is RatesState.Ready -> {
                    val quote = state.snapshot.quoteFor(currency)
                    if (quote == null) {
                        LocalValue.Unavailable
                    } else {
                        LocalValue.Value(currency.convert(amount, quote.rate), currency, quote.stale, quote.asOf)
                    }
                }
            }
    }
}
