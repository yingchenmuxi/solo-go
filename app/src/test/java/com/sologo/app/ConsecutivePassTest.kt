package com.sologo.app

import com.sologo.app.ui.game.hasTwoConsecutivePasses
import com.sologo.core.model.GoPoint
import com.sologo.core.model.MoveRecord
import com.sologo.core.model.StoneColor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsecutivePassTest {
    @Test
    fun twoPassesTriggerEndGamePromptCondition() {
        val moves = listOf(
            MoveRecord(StoneColor.BLACK, null, moveNumber = 1),
            MoveRecord(StoneColor.WHITE, null, moveNumber = 2)
        )

        assertTrue(hasTwoConsecutivePasses(moves))
    }

    @Test
    fun stoneMoveBreaksPassSequence() {
        val moves = listOf(
            MoveRecord(StoneColor.BLACK, null, moveNumber = 1),
            MoveRecord(StoneColor.WHITE, GoPoint(3, 3), moveNumber = 2),
            MoveRecord(StoneColor.BLACK, null, moveNumber = 3)
        )

        assertFalse(hasTwoConsecutivePasses(moves))
    }
}
