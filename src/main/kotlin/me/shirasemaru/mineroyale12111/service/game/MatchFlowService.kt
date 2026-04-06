package me.shirasemaru.mineroyale12111.service.game

import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.game.GameState
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import org.bukkit.entity.Player

class MatchFlowService {

    sealed interface StartCheckResult {
        data object Ready : StartCheckResult
        data object AlreadyInProgress : StartCheckResult
        data object NotEnoughPlayers : StartCheckResult
        data object TooManyPlayers : StartCheckResult
    }

    sealed interface EliminationResult {
        data object Ignored : EliminationResult
        data class Eliminated(
            val aliveCount: Int,
            val winner: Player?
        ) : EliminationResult
    }

    fun evaluateStart(
        session: GameSession,
        playerCount: Int,
        minPlayers: Int,
        maxPlayers: Int
    ): StartCheckResult {
        if (session.state != GameState.WAITING) {
            return StartCheckResult.AlreadyInProgress
        }

        if (playerCount < minPlayers) {
            return StartCheckResult.NotEnoughPlayers
        }

        if (playerCount > maxPlayers) {
            return StartCheckResult.TooManyPlayers
        }

        return StartCheckResult.Ready
    }

    fun canStop(session: GameSession): Boolean =
        session.state != GameState.WAITING

    fun moveToCountdown(session: GameSession) {
        session.state = GameState.COUNTDOWN
    }

    fun cancelCountdown(session: GameSession) {
        session.resetToWaiting()
    }

    fun moveToRunning(session: GameSession) {
        session.state = GameState.RUNNING
        session.pvpEnabled = false
    }

    fun canFinish(session: GameSession): Boolean =
        session.state == GameState.RUNNING

    fun moveToEnding(session: GameSession) {
        session.state = GameState.ENDING
    }

    fun processElimination(
        session: GameSession,
        playerRegistry: PlayerRegistry,
        player: Player
    ): EliminationResult {
        if (session.state != GameState.RUNNING) {
            return EliminationResult.Ignored
        }

        if (!playerRegistry.markEliminated(player)) {
            return EliminationResult.Ignored
        }

        return EliminationResult.Eliminated(
            aliveCount = playerRegistry.aliveCount(),
            winner = if (playerRegistry.aliveCount() <= 1) {
                playerRegistry.firstAlivePlayer()
            } else {
                null
            }
        )
    }
}
