package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GameManager(
    val plugin: JavaPlugin,
    val configManager: ConfigManager,
    private val scoreboardManager: ScoreboardManager
) {

    var state = GameState.WAITING
        private set

    private val playerManager = PlayerManager()
    private val borderManager = BorderManager(plugin, configManager)

    private var gameTask: BukkitTask? = null
    private var remainingGameSeconds: Int = 0
    private var currentPhase: Int = 0
    private var remainingPhaseSeconds: Int = 0

    fun startGame() {

        if (state != GameState.WAITING) return

        state = GameState.RUNNING

        playerManager.registerAllOnline()

        teleportPlayersRandomly() // 追加

        borderManager.initialize()

        val phases = configManager.loadBorderPhases()

        remainingGameSeconds = configManager.gameDurationSeconds.toInt()

        startGameTimer()
        startPhaseTracking(phases)

        borderManager.runPhases(phases) {
            endGame(null)
        }

        Bukkit.broadcastMessage("§aゲーム開始！")
    }

    /*
     * =========================
     * ランダムテレポート
     * =========================
     */
    private fun teleportPlayersRandomly() {

        val world = configManager.gameWorld
        val centerX = configManager.teleportCenterX
        val centerZ = configManager.teleportCenterZ
        val radius = configManager.teleportRadius

        playerManager.getAlivePlayers().forEach { gamePlayer ->

            val player: Player = gamePlayer.player ?: return@forEach

            val angle = Random().nextDouble() * 2 * Math.PI
            val distance = sqrt(Random().nextDouble()) * radius

            val x = centerX + cos(angle) * distance
            val z = centerZ + sin(angle) * distance

            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1

            val location = Location(world, x, y.toDouble(), z)

            player.teleport(location)
        }
    }

    private fun startGameTimer() {

        gameTask?.cancel()

        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (remainingGameSeconds <= 0) {
                endGame(null)
                return@Runnable
            }

            remainingGameSeconds--
            remainingPhaseSeconds--

            scoreboardManager.updateAll(
                state,
                playerManager.getAlivePlayers().size,
                remainingGameSeconds,
                currentPhase,
                remainingPhaseSeconds.coerceAtLeast(0)
            )

        }, 20L, 20L)
    }

    private fun startPhaseTracking(phases: List<BorderPhase>) {

        var accumulated = 0
        currentPhase = 1

        remainingPhaseSeconds = phases.firstOrNull()?.durationSeconds?.toInt() ?: 0

        phases.forEachIndexed { index, phase ->
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                currentPhase = index + 1
                remainingPhaseSeconds = phase.durationSeconds.toInt()
            }, accumulated * 20L)

            accumulated += phase.durationSeconds.toInt()
        }
    }

    fun handleDeath(player: Player) {

        if (state != GameState.RUNNING) return

        val uuid = player.uniqueId

        if (!playerManager.isAlive(uuid)) return

        playerManager.markDead(uuid)
        player.sendMessage("§cあなたは脱落しました。")

        checkWinCondition()
    }

    private fun checkWinCondition() {

        val alivePlayers = playerManager.getAlivePlayers()

        when (alivePlayers.size) {
            1 -> endGame(alivePlayers.first())
            0 -> endGame(null)
        }
    }

    fun endGame(winner: GamePlayer?) {

        if (state == GameState.ENDING) return

        state = GameState.ENDING

        gameTask?.cancel()
        borderManager.stop()

        if (winner != null) {
            Bukkit.broadcastMessage("§6勝者: §a${winner.player?.name}")
        } else {
            Bukkit.broadcastMessage("§6勝者なし。")
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            resetGame()
        }, 100L)
    }

    private fun resetGame() {

        borderManager.reset()
        playerManager.clear()

        state = GameState.WAITING

        Bukkit.broadcastMessage("§7ゲームがリセットされました。")
    }
}