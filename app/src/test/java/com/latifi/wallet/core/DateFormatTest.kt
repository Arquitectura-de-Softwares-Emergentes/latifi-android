package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class DateFormatTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val march3 = Instant.parse("2026-03-03T15:30:00Z")
    private val march3NextYear = Instant.parse("2027-03-03T15:30:00Z")
    private val en = Locale.forLanguageTag("en-US")
    private val es = Locale.forLanguageTag("es-419")

    @Test
    fun englishShowsTheMonthAndDay() {
        assertEquals("March 3", DateFormat.short(march3, en, today, ZoneOffset.UTC))
    }

    @Test
    fun spanishShowsTheDayBeforeTheMonth() {
        assertEquals("3 de marzo", DateFormat.short(march3, es, today, ZoneOffset.UTC))
    }

    @Test
    fun theYearAppearsOnlyWhenItDiffersFromThisYear() {
        assertEquals("March 3, 2027", DateFormat.short(march3NextYear, en, today, ZoneOffset.UTC))
        assertEquals("3 de marzo de 2027", DateFormat.short(march3NextYear, es, today, ZoneOffset.UTC))
    }

    @Test
    fun theZoneDecidesTheDay() {
        val lateEvening = Instant.parse("2026-03-03T23:30:00Z")
        assertEquals("March 3", DateFormat.short(lateEvening, en, today, ZoneOffset.UTC))
        assertEquals("March 4", DateFormat.short(lateEvening, en, today, ZoneOffset.ofHours(3)))
    }

    @Test
    fun aStampOfTodayIsOnlyATimeAndAnOlderOneCarriesTheDate() {
        val now = Instant.parse("2026-11-10T09:05:00Z")
        val sameDay = DateFormat.stamp(now, en, today, ZoneOffset.UTC)
        assertTrue(sameDay, sameDay.contains("9:05"))
        assertTrue(sameDay, !sameDay.contains("November"))
        val older = DateFormat.stamp(march3, en, today, ZoneOffset.UTC)
        assertTrue(older, older.startsWith("March 3, "))
    }
}
