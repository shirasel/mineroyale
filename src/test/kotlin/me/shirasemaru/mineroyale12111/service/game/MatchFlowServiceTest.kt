package me.shirasemaru.mineroyale12111.service.game

import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.game.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchFlowServiceTest {

    private val service = MatchFlowService()

    @Test
    fun `evaluateStart returns ready when waiting and player count is in range`() {
        val session = GameSession(state = GameState.WAITING)

        val result = service.evaluateStart(
            session = session,
            playerCount = 4,
            minPlayers = 2,
            maxPlayers = 20
        )

        assertEquals(MatchFlowService.StartCheckResult.Ready, result)
    }

    @Test
    fun `evaluateStart returns already in progress when session is not waiting`() {
        val session = GameSession(state = GameState.RUNNING)

        val result = service.evaluateStart(
            session = session,
            playerCount = 4,
            minPlayers = 2,
            maxPlayers = 20
        )

        assertEquals(MatchFlowService.StartCheckResult.AlreadyInProgress, result)
    }

    @Test
    fun `evaluateStart returns not enough players when under minimum`() {
        val session = GameSession(state = GameState.WAITING)

        val result = service.evaluateStart(
            session = session,
            playerCount = 1,
            minPlayers = 2,
            maxPlayers = 20
        )

        assertEquals(MatchFlowService.StartCheckResult.NotEnoughPlayers, result)
    }

    @Test
    fun `evaluateStart returns too many players when over maximum`() {
        val session = GameSession(state = GameState.WAITING)

        val result = service.evaluateStart(
            session = session,
            playerCount = 21,
            minPlayers = 2,
            maxPlayers = 20
        )

        assertEquals(MatchFlowService.StartCheckResult.TooManyPlayers, result)
    }

    @Test
    fun `moveToCountdown updates session state`() {
        val session = GameSession()

        service.moveToCountdown(session)

        assertEquals(GameState.COUNTDOWN, session.state)
    }

    @Test
    fun `moveToRunning updates session state and disables pvp`() {
        val session = GameSession(state = GameState.COUNTDOWN, pvpEnabled = true)

        service.moveToRunning(session)

        assertEquals(GameState.RUNNING, session.state)
        assertFalse(session.pvpEnabled)
    }

    @Test
    fun `moveToEnding updates session state`() {
        val session = GameSession(state = GameState.RUNNING)

        service.moveToEnding(session)

        assertEquals(GameState.ENDING, session.state)
    }

    @Test
    fun `cancelCountdown resets session to waiting`() {
        val session = GameSession(
            state = GameState.COUNTDOWN,
            participantCount = 5,
            countdownRemainingSeconds = 10
        )

        service.cancelCountdown(session)

        assertEquals(GameState.WAITING, session.state)
        assertEquals(0, session.participantCount)
        assertEquals(0, session.countdownRemainingSeconds)
    }

    @Test
    fun `canStop is false only while waiting`() {
        assertFalse(service.canStop(GameSession(state = GameState.WAITING)))
        assertTrue(service.canStop(GameSession(state = GameState.COUNTDOWN)))
        assertTrue(service.canStop(GameSession(state = GameState.RUNNING)))
    }

    @Test
    fun `canFinish is true only while running`() {
        assertFalse(service.canFinish(GameSession(state = GameState.WAITING)))
        assertFalse(service.canFinish(GameSession(state = GameState.COUNTDOWN)))
        assertTrue(service.canFinish(GameSession(state = GameState.RUNNING)))
        assertFalse(service.canFinish(GameSession(state = GameState.ENDING)))
    }
}
