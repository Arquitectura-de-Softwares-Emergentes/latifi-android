package com.latifi.wallet.data.fake

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.Page
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.model.ReputationSummary
import com.latifi.wallet.domain.reputation.ReputationRepository
import java.math.BigInteger
import java.time.Clock
import java.time.Duration

/**
 * In-memory reputation for the demo build and for tests, shaped like the API (OpenAPI 0.2.0).
 *
 * The demo wallet has score 620 (Trusted), counters 3, 1 and 0, and a history of 12 events with
 * one profile event and one late payment. The counters are the API summary and the history rows
 * are events, so the two numbers do not have to match one to one, as in the real data. Every
 * borrower of [FakeData] has a score at its level: Lucia Excellent, Mateo Trusted, Camila Building
 * and the new borrower New. An address with no seed answers `ReputationNotFound`.
 *
 * A test queues a failure with [failNext], reads [calls] to see what the screen asked for, and
 * adds or removes a profile with [seed] and [remove].
 */
class FakeReputationRepository(clock: Clock = Clock.systemUTC()) : ReputationRepository {
    private val summaries = mutableMapOf<String, ReputationSummary>()
    private val histories = mutableMapOf<String, List<ReputationEvent>>()

    /** The next call to [get] or [history] fails with this error, once. */
    var failNext: AppError? = null

    /** One entry per call, in order: the address in lowercase and the type filter of a history call. */
    val calls = mutableListOf<Pair<String, ReputationEventType?>>()

    init {
        val now = clock.instant()
        seed(
            FakeData.DEMO_ADDRESS,
            ReputationSummary(620, ReputationLevel.TRUSTED, 3, 1, 0, true, now.minus(Duration.ofHours(2))),
            demoHistory(clock),
        )
        seedGenerated(clock, FakeData.LUCIA, 820, onTime = 8, late = 0, defaulted = 0)
        seedGenerated(clock, FakeData.MATEO, 650, onTime = 5, late = 1, defaulted = 0)
        seedGenerated(clock, FakeData.CAMILA, 450, onTime = 2, late = 1, defaulted = 0)
        seedGenerated(clock, FakeData.NEW_BORROWER, 120, onTime = 0, late = 0, defaulted = 1)
    }

    /** Puts a profile in place. [history] must be newest first. */
    fun seed(address: String, summary: ReputationSummary, history: List<ReputationEvent> = emptyList()) {
        summaries[key(address)] = summary
        histories[key(address)] = history
    }

    /** Removes the profile, so the address answers `ReputationNotFound`. */
    fun remove(address: String) {
        summaries.remove(key(address))
        histories.remove(key(address))
    }

    override suspend fun get(address: String): Result<ReputationSummary> {
        calls += key(address) to null
        takeFailure()?.let { return Result.failure(AppException(it)) }
        val summary = summaries[key(address)] ?: return Result.failure(AppException(AppError.ReputationNotFound))
        return Result.success(summary)
    }

    override suspend fun history(
        address: String,
        type: ReputationEventType?,
        page: Int,
        size: Int,
    ): Result<Page<ReputationEvent>> {
        calls += key(address) to type
        takeFailure()?.let { return Result.failure(AppException(it)) }
        if (page < 0) return Result.failure(AppException(AppError.Validation("page")))
        if (size !in 1..MAX_SIZE) return Result.failure(AppException(AppError.Validation("size")))
        val all = histories[key(address)] ?: return Result.failure(AppException(AppError.ReputationNotFound))

        // Newest first, ties by id so the order never depends on how the seed was built.
        val matching = all
            .filter { type == null || it.type == type }
            .sortedWith(compareByDescending<ReputationEvent> { it.occurredAt }.thenByDescending { it.id })
        val from = page.toLong() * size.toLong()
        val items = if (from >= matching.size) emptyList() else matching.subList(from.toInt(), minOf(matching.size, (from + size).toInt()))
        return Result.success(Page(items, page, size, matching.size.toLong()))
    }

    private fun takeFailure(): AppError? = failNext.also { failNext = null }

    private fun key(address: String) = address.lowercase()

    private fun seedGenerated(clock: Clock, address: String, score: Int, onTime: Int, late: Int, defaulted: Int) {
        val now = clock.instant()
        val summary = ReputationSummary(score, ReputationLevel.fromScore(score), onTime, late, defaulted, true, now.minus(Duration.ofHours(5)))
        seed(address, summary, generatedHistory(clock, score, onTime, late, defaulted))
    }

    /** Events oldest to newest with running totals that end exactly at [score], returned newest first. */
    private fun generatedHistory(clock: Clock, score: Int, onTime: Int, late: Int, defaulted: Int): List<ReputationEvent> {
        val rest = score - late * LATE_DELTA - defaulted * DEFAULT_DELTA
        // The profile event takes what the on-time events do not, so the totals always land on [score].
        val profileDelta = if (onTime == 0) rest else PROFILE_DELTA
        val onTimeTotal = rest - profileDelta
        val shares = MutableList(onTime) { onTimeTotal / onTime }
        if (onTime > 0) shares[shares.lastIndex] += onTimeTotal % onTime
        val types = buildList {
            add(Row(ReputationEventType.PROFILE_COMPLETED, profileDelta, null, null, 0))
            shares.forEach { add(Row(ReputationEventType.REPAID_ON_TIME, it, 0, null, 0)) }
            repeat(late) { add(Row(ReputationEventType.REPAID_LATE, LATE_DELTA, 0, GENERATED_DAYS_LATE, 0)) }
            repeat(defaulted) { add(Row(ReputationEventType.DEFAULTED, DEFAULT_DELTA, 0, null, 0)) }
        }
        // One event a week, the newest a week ago.
        val rows = types.mapIndexed { index, row ->
            row.copy(loan = if (row.loan == null) null else (index + 1).toLong(), daysAgo = (types.size - index).toLong() * DAYS_PER_WEEK)
        }
        return build(clock, rows, idBase = score.toLong())
    }

    private fun demoHistory(clock: Clock): List<ReputationEvent> {
        val onTime = ReputationEventType.REPAID_ON_TIME
        // Oldest first. The running total ends at 620.
        val rows = listOf(
            Row(ReputationEventType.PROFILE_COMPLETED, 50, null, null, 160),
            Row(onTime, 70, 3, null, 118),
            Row(onTime, 60, 7, null, 75),
            Row(onTime, 60, 7, null, 60),
            Row(ReputationEventType.REPAID_LATE, 10, 7, 5, 40),
            Row(onTime, 60, 7, null, 30),
            Row(onTime, 80, 11, null, 25),
            Row(onTime, 70, 11, null, 20),
            Row(onTime, 60, 11, null, 15),
            Row(onTime, 40, 11, null, 8),
            Row(onTime, 40, 11, null, 4),
            Row(onTime, 20, 11, null, 2),
        )
        return build(clock, rows, idBase = 0)
    }

    /** Turns rows (oldest first) into events with a running total, and returns them newest first. */
    private fun build(clock: Clock, rows: List<Row>, idBase: Long): List<ReputationEvent> {
        val now = clock.instant()
        var running = 0
        return rows.mapIndexed { index, row ->
            running += row.delta
            val id = idBase + index + 1
            ReputationEvent(
                id = id,
                loanId = row.loan?.let(BigInteger::valueOf),
                type = row.type,
                delta = row.delta,
                scoreAfter = running,
                daysLate = row.daysLate,
                txHash = if (row.loan == null) null else "0x" + id.toString(16).padStart(HASH_DIGITS, '0'),
                logIndex = if (row.loan == null) null else 0,
                occurredAt = now.minus(Duration.ofDays(row.daysAgo)),
            )
        }.reversed()
    }

    /** One seed row: [loan] is null for the profile event, [daysAgo] counts back from the clock. */
    private data class Row(val type: ReputationEventType, val delta: Int, val loan: Long?, val daysLate: Int?, val daysAgo: Long)

    companion object {
        const val MAX_SIZE = 100
        private const val HASH_DIGITS = 64
        private const val PROFILE_DELTA = 50
        private const val LATE_DELTA = 10
        private const val DEFAULT_DELTA = -50
        private const val GENERATED_DAYS_LATE = 4
        private const val DAYS_PER_WEEK = 7L
    }
}
