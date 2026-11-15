package com.latifi.wallet.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Short, locale-aware dates for the loan screens: `March 3` in English and `3 de marzo` in
 * Spanish. The year joins the text only when it differs from the current year. The pattern is
 * explicit because the platform skeleton API is not available on the JVM and below API 34.
 */
object DateFormat {

    /** `March 3`, or `March 3, 2027` when the year is not [today]'s year. */
    fun short(instant: Instant, locale: Locale, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = instant.atZone(zone).toLocalDate()
        val spanish = locale.language == "es"
        val pattern = when {
            spanish && date.year == today.year -> "d 'de' MMMM"
            spanish -> "d 'de' MMMM 'de' yyyy"
            date.year == today.year -> "MMMM d"
            else -> "MMMM d, yyyy"
        }
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    /**
     * The moment of a rate quote: only the time when it is from [today], else the short date and
     * the time. Used by the stale caption `Rate from {time}`.
     */
    fun stamp(instant: Instant, locale: Locale, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))
        val date = instant.atZone(zone).toLocalDate()
        return if (date == today) time else short(instant, locale, today, zone) + ", " + time
    }
}
