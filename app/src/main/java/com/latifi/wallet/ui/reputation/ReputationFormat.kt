package com.latifi.wallet.ui.reputation

import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.domain.model.ReputationLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Small pure helpers for the reputation screen, kept out of the composables so a JVM test can
 * prove them. The words come from resources: this object only decides the sign, the size, the
 * bar fill and the date.
 */
object ReputationFormat {
    /** The highest score. The bar fills against it and the screen says "N of 1000". */
    const val MAX_SCORE = 1000

    /** A points change split for the `rep_event_points` plural: its sign text and its size. */
    data class Points(val sign: String, val magnitude: Int)

    /** `+` above zero, `-` below zero and nothing for zero, so a change reads +20, -5 or 0. */
    fun points(delta: Int): Points =
        Points(
            sign = when {
                delta > 0 -> "+"
                delta < 0 -> "-"
                else -> ""
            },
            magnitude = abs(delta),
        )

    /** Where a plural of English or Spanish picks its text: `one` for exactly 1, `other` for the rest. */
    fun pluralKey(quantity: Int): String = if (quantity == 1) "one" else "other"

    /** The share of the bar a [score] fills, from 0 to 1. A score outside 0 to 1000 is clamped. */
    fun barFraction(score: Int): Float = score.coerceIn(0, MAX_SCORE).toFloat() / MAX_SCORE

    /** The level that [score] falls in. */
    fun levelOf(score: Int): ReputationLevel = ReputationLevel.fromScore(score)

    /** `January 5` or `5 de enero`, with the year only when it is not this year. */
    fun date(instant: Instant, locale: Locale, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String =
        DateFormat.short(instant, locale, today, zone)

    /** The "as of" moment: the time only when it is today, else the date and the time. */
    fun updated(instant: Instant, locale: Locale, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String =
        DateFormat.stamp(instant, locale, today, zone)
}
