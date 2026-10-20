package com.sologo.app

import com.sologo.core.model.BoardPosition
import com.sologo.core.model.GoPoint
import com.sologo.core.model.ScoreSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreSummaryTest {
    @Test
    fun countingUsesAllIntersectionsAndAppliesKomi() {
        val ownership = List(9 * 9) { index -> if (index < 40) 1f else -1f }
        val summary = requireNotNull(
            ScoreSummary.fromOwnership(
                ownership = ownership,
                scoringBoard = BoardPosition.empty(9),
                komi = 7.5f
            )
        )

        assertEquals(40, summary.blackPoints)
        assertEquals(41, summary.whitePoints)
        assertEquals(-8.5, summary.lead, 0.0001)
    }

    @Test
    fun territoryExcludesOccupiedPointsButAreaIncludesThem() {
        val blackStone = GoPoint(0, 0)
        val board = BoardPosition.fromSetup(9, setOf(blackStone), emptySet())
        val ownership = List(9 * 9) { 1f }
        val summary = requireNotNull(
            ScoreSummary.fromOwnership(
                ownership = ownership,
                scoringBoard = board,
                komi = 7.5f
            )
        )

        assertEquals(81, summary.blackPoints)
        assertEquals(80, summary.blackTerritory)
        assertEquals(0, summary.whiteTerritory)
    }

    @Test
    fun markersLeaveStonePointsUnmarked() {
        val blackStone = GoPoint(0, 0)
        val board = BoardPosition.fromSetup(9, setOf(blackStone), emptySet())
        val markers = ScoreSummary.emptyPointMarkers(
            ownership = List(9 * 9) { -1f },
            scoringBoard = board
        )

        assertEquals(81, markers.size)
        assertEquals(0f, markers[0], 0.0001f)
        assertEquals(-1f, markers[1], 0.0001f)
    }

    @Test
    fun missingOwnershipReturnsNull() {
        assertNull(
            ScoreSummary.fromOwnership(
                ownership = emptyList(),
                scoringBoard = BoardPosition.empty(9),
                komi = 7.5f
            )
        )
    }
}
