package me.shirasemaru.mineroyale12111.service.game

import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.service.border.BorderManager
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import me.shirasemaru.mineroyale12111.service.player.PlayerSetupService
import me.shirasemaru.mineroyale12111.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class MatchLifecycleService(
    private val plugin: JavaPlugin,
    private val scoreboardManager: ScoreboardManager,
    private val playerRegistry: PlayerRegistry,
    private val playerSetupService: PlayerSetupService,
    private val borderManager: BorderManager,
    private val compassTrackingService: CompassTrackingService,
    private val victoryService: VictoryService,
    private val messageService: MessageService,
    private val matchFlowService: MatchFlowService
) {

    private var scoreboardTask: BukkitTask? = null

    fun getEligiblePlayers(): List<Player> =
        Bukkit.getOnlinePlayers()
            .filter { it.gameMode != GameMode.SPECTATOR }
            .sortedBy { it.name }

    fun startMatch(session: GameSession, players: List<Player>, onMatchComplete: () -> Unit) {
        matchFlowService.moveToRunning(session)
        playerRegistry.resetForMatch(players)
        session.participantCount = players.size
        session.aliveCount = players.size

        borderManager.initialize(session)
        playerSetupService.prepareMatchPlayers(borderManager.generateRandomSpawnLocations(players))
        borderManager.runPhases(session, onMatchComplete)
        startScoreboardTask(session)
        compassTrackingService.start(playerRegistry::getAlivePlayers)
        messageService.logMatchStarted(plugin.logger)
    }

    fun stopCurrentMatch(session: GameSession) {
        stopRealtimeSystems()
        borderManager.reset(session)
        messageService.broadcastGameStopped()
        resetGame(session)
    }

    fun finishMatch(session: GameSession, winner: Player?, onResetCompleted: (() -> Unit)? = null) {
        matchFlowService.moveToEnding(session)
        stopRealtimeSystems()
        borderManager.reset(session)

        if (winner != null && winner.isOnline) {
            victoryService.playVictory(winner) {
                resetGame(session)
                onResetCompleted?.invoke()
            }
        } else {
            messageService.broadcastNoWinner()
            resetGame(session)
            onResetCompleted?.invoke()
        }
    }

    private fun stopRealtimeSystems() {
        stopScoreboardTask()
        compassTrackingService.stop()
        borderManager.stop()
    }

    private fun resetGame(session: GameSession) {
        playerSetupService.resetAllOnlinePlayersToLobby()
        scoreboardManager.clear()
        playerRegistry.clear()
        session.resetToWaiting()
    }

    private fun startScoreboardTask(session: GameSession) {
        scoreboardTask?.cancel()

        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!matchFlowService.canFinish(session)) return@Runnable

            session.aliveCount = playerRegistry.aliveCount()
            scoreboardManager.update(session)
        }, 0L, 20L)
    }

    private fun stopScoreboardTask() {
        scoreboardTask?.cancel()
        scoreboardTask = null
    }
}
