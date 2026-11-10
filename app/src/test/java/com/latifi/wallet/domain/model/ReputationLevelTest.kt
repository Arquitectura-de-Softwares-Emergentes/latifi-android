package com.latifi.wallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReputationLevelTest {
    @Test
    fun boundaryScoresFallInTheRightLevel() {
        val expected = mapOf(
            0 to ReputationLevel.NEW,
            399 to ReputationLevel.NEW,
            400 to ReputationLevel.BUILDING,
            599 to ReputationLevel.BUILDING,
            600 to ReputationLevel.TRUSTED,
            799 to ReputationLevel.TRUSTED,
            800 to ReputationLevel.EXCELLENT,
            1000 to ReputationLevel.EXCELLENT,
        )
        for ((score, level) in expected) {
            assertEquals("score $score", level, ReputationLevel.fromScore(score))
        }
    }

    @Test
    fun lowerBoundsMatchTheLevelRanges() {
        assertEquals(
            listOf(0, 400, 600, 800),
            ReputationLevel.entries.map { it.minScore },
        )
    }

    @Test
    fun aScoreOutsideTheScaleIsClamped() {
        assertEquals(ReputationLevel.NEW, ReputationLevel.fromScore(-5))
        assertEquals(ReputationLevel.EXCELLENT, ReputationLevel.fromScore(1200))
    }
}
