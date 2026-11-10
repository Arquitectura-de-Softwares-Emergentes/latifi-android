package com.latifi.wallet.ui.reputation

import com.latifi.wallet.guard.ResourceScan
import com.latifi.wallet.domain.model.ReputationLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class ReputationFormatTest {
    private val english = Locale.forLanguageTag("en-US")
    private val spanish = Locale.forLanguageTag("es-419")
    private val today = LocalDate.of(2026, 11, 10)

    private val enPlurals = ResourceScan.load(ResourceScan.DEFAULT_VALUES).plurals.getValue("rep_event_points")
    private val esPlurals = ResourceScan.load(ResourceScan.SPANISH_VALUES).plurals.getValue("rep_event_points")

    /** What the screen shows: the plural item for the size of the change, filled with its sign and size. */
    private fun render(plurals: Map<String, String>, delta: Int): String {
        val points = ReputationFormat.points(delta)
        return plurals.getValue(ReputationFormat.pluralKey(points.magnitude)).format(points.sign, points.magnitude)
    }

    @Test
    fun aGainHasAPlusSignAndItsSize() {
        assertEquals(ReputationFormat.Points("+", 20), ReputationFormat.points(20))
    }

    @Test
    fun aLossHasAMinusSignAndItsSize() {
        assertEquals(ReputationFormat.Points("-", 5), ReputationFormat.points(-5))
    }

    @Test
    fun noChangeHasNoSign() {
        assertEquals(ReputationFormat.Points("", 0), ReputationFormat.points(0))
    }

    @Test
    fun theEnglishTextReadsAsPlusTwentyPoints() {
        assertEquals("+20 points", render(enPlurals, 20))
        assertEquals("-5 points", render(enPlurals, -5))
        assertEquals("0 points", render(enPlurals, 0))
    }

    @Test
    fun theSingularFormIsUsedForExactlyOnePoint() {
        assertEquals("+1 point", render(enPlurals, 1))
        assertEquals("-1 point", render(enPlurals, -1))
        assertEquals("+1 punto", render(esPlurals, 1))
    }

    @Test
    fun theSpanishTextReadsAsPlusTwentyPuntos() {
        assertEquals("+20 puntos", render(esPlurals, 20))
        assertEquals("-5 puntos", render(esPlurals, -5))
        assertEquals("0 puntos", render(esPlurals, 0))
    }

    @Test
    fun theEventsCounterHasASingularAndAPluralInBothLanguages() {
        val en = ResourceScan.load(ResourceScan.DEFAULT_VALUES).plurals.getValue("counter_events")
        val es = ResourceScan.load(ResourceScan.SPANISH_VALUES).plurals.getValue("counter_events")

        assertEquals("1 event", en.getValue(ReputationFormat.pluralKey(1)).format(1))
        assertEquals("12 events", en.getValue(ReputationFormat.pluralKey(12)).format(12))
        assertEquals("0 events", en.getValue(ReputationFormat.pluralKey(0)).format(0))
        assertEquals("1 evento", es.getValue(ReputationFormat.pluralKey(1)).format(1))
        assertEquals("12 eventos", es.getValue(ReputationFormat.pluralKey(12)).format(12))
    }

    @Test
    fun theBarFillsInProportionToTheScoreAndIsClamped() {
        assertEquals(0f, ReputationFormat.barFraction(0), 0f)
        assertEquals(0.62f, ReputationFormat.barFraction(620), 0.0001f)
        assertEquals(1f, ReputationFormat.barFraction(1000), 0f)
        assertEquals(0f, ReputationFormat.barFraction(-30), 0f)
        assertEquals(1f, ReputationFormat.barFraction(1500), 0f)
    }

    @Test
    fun theLevelFollowsTheScoreRanges() {
        assertEquals(ReputationLevel.NEW, ReputationFormat.levelOf(399))
        assertEquals(ReputationLevel.BUILDING, ReputationFormat.levelOf(400))
        assertEquals(ReputationLevel.BUILDING, ReputationFormat.levelOf(599))
        assertEquals(ReputationLevel.TRUSTED, ReputationFormat.levelOf(600))
        assertEquals(ReputationLevel.TRUSTED, ReputationFormat.levelOf(799))
        assertEquals(ReputationLevel.EXCELLENT, ReputationFormat.levelOf(800))
        assertEquals(ReputationLevel.EXCELLENT, ReputationFormat.levelOf(1000))
    }

    @Test
    fun theDateIsLocalizedAndDropsTheCurrentYear() {
        val instant = Instant.parse("2026-01-05T12:00:00Z")

        assertEquals("January 5", ReputationFormat.date(instant, english, today, ZoneOffset.UTC))
        assertEquals("5 de enero", ReputationFormat.date(instant, spanish, today, ZoneOffset.UTC))
        val old = Instant.parse("2025-01-05T12:00:00Z")
        assertEquals("January 5, 2025", ReputationFormat.date(old, english, today, ZoneOffset.UTC))
    }

    @Test
    fun theUpdatedTimeShowsOnlyTheTimeOnTheSameDay() {
        val sameDay = Instant.parse("2026-11-10T09:30:00Z")
        val earlier = Instant.parse("2026-11-08T09:30:00Z")

        val sameDayText = ReputationFormat.updated(sameDay, english, today, ZoneOffset.UTC)
        val earlierText = ReputationFormat.updated(earlier, english, today, ZoneOffset.UTC)

        assertEquals(false, sameDayText.contains("November"))
        assertEquals(true, earlierText.startsWith("November 8, "))
    }
}
