package com.sologo.app

import com.sologo.core.model.TerritorySummary
import com.sologo.core.model.StoneColor
import com.sologo.core.model.normalizeOwnershipToBlack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerritorySummaryTest {
    @Test
    fun blackOwnershipIsCountedAsBlackArea() {
        val summary = requireNotNull(
            TerritorySummary.fromOwnership(
                ownership = List(9 * 9) { 1f },
                boardSize = 9,
                komi = 7.5f
            )
        )

        assertEquals(81.0, summary.blackArea, 0.0001)
        assertEquals(0.0, summary.whiteArea, 0.0001)
        assertEquals(73.5, summary.lead, 0.0001)
    }

    @Test
    fun komiIsDeductedFromBlackLead() {
        val ownership = List(9 * 9) { index -> if (index < 40) 1f else -1f }
        val summary = requireNotNull(
            TerritorySummary.fromOwnership(
                ownership = ownership,
                boardSize = 9,
                komi = 7.5f
            )
        )

        assertEquals(40.0, summary.blackArea, 0.0001)
        assertEquals(41.0, summary.whiteArea, 0.0001)
        assertEquals(-8.5, summary.lead, 0.0001)
    }

    @Test
    fun missingOwnershipReturnsNull() {
        assertNull(TerritorySummary.fromOwnership(emptyList(), boardSize = 9, komi = 7.5f))
    }

    @Test
    fun whiteTurnOwnershipIsFlippedToBlackPerspective() {
        val normalized = normalizeOwnershipToBlack(
            values = listOf(0.4f, -0.6f),
            turn = StoneColor.WHITE
        )

        assertEquals(-0.4f, normalized[0], 0.0001f)
        assertEquals(0.6f, normalized[1], 0.0001f)
    }
}
