package me.shirasemaru.mineroyale12111.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GameSessionTest {

    @Test
    fun `resetToWaiting clears runtime session state`() {
        val session = GameSession(
            state = GameState.RUNNING,
            pvpEnabled = true,
            participantCount = 12,
            aliveCount = 4,
            countdownRemainingSeconds = 7,
            currentPhase = 3,
            totalPhases = 5,
            phaseState = "縮小中",
            remainingPhaseSeconds = 25,
            remainingGameSeconds = 120
        )

        session.resetToWaiting()

        assertEquals(GameState.WAITING, session.state)
        assertFalse(session.pvpEnabled)
        assertEquals(0, session.participantCount)
        assertEquals(0, session.aliveCount)
        assertEquals(0, session.countdownRemainingSeconds)
        assertEquals(0, session.currentPhase)
        assertEquals(0, session.totalPhases)
        assertEquals(PhaseState.IDLE.displayName, session.phaseState)
        assertEquals(0, session.remainingPhaseSeconds)
        assertEquals(0, session.remainingGameSeconds)
    }
}
