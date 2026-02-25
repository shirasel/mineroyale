package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.*
import kotlin.random.Random

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

    /*
     * =========================
     * ゲーム開始
     * =========================
     */
    fun startGame() {

        if (state != GameState.WAITING) return

        state = GameState.RUNNING

        playerManager.registerAllOnline()

        borderManager.initialize()
        teleportPlayersSafely()

        remainingGameSeconds = calculateTotalGameTime()
        startGameTimer()

        borderManager.runPhases {
            handleFinalPhaseFinished()
        }

        Bukkit.broadcastMessage("§aゲーム開始！")
    }

    /*
     * =========================
     * フェーズ合計時間
     * =========================
     */
    private fun calculateTotalGameTime(): Int {
        return configManager.borderPhases.sumOf { it.wait + it.duration }
    }

    /*
     * =========================
     * 安全テレポート
     * =========================
     */
    private fun teleportPlayersSafely() {

        val world = configManager.gameWorld
        val centerX = borderManager.getCurrentCenterX()
        val centerZ = borderManager.getCurrentCenterZ()

        val radius = configManager.startSize / 2 - 5
        val minDistance = 15.0

        val usedLocations = mutableListOf<Location>()

        playerManager.getAlivePlayers().forEach { gamePlayer ->

            val player = gamePlayer.player ?: return@forEach

            var location: Location
            var attempts = 0

            do {
                location = generateSafeLocation(world, centerX, centerZ, radius)
                attempts++
            } while (!isFarEnough(location, usedLocations, minDistance) && attempts < 30)

            usedLocations.add(location)
            player.teleport(location)
        }
    }

    private fun generateSafeLocation(
        world: World,
        centerX: Double,
        centerZ: Double,
        radius: Double
    ): Location {

        val angle = Random.nextDouble() * 2 * PI
        val distance = sqrt(Random.nextDouble()) * radius

        val x = centerX + cos(angle) * distance
        val z = centerZ + sin(angle) * distance

        var y = world.getHighestBlockYAt(x.toInt(), z.toInt())
        val block = world.getBlockAt(x.toInt(), y, z.toInt())

        if (block.type == Material.LAVA || block.type == Material.WATER) {
            y += 2
        }

        return Location(world, x, (y + 1).toDouble(), z)
    }

    private fun isFarEnough(
        location: Location,
        others: List<Location>,
        minDistance: Double
    ): Boolean {
        return others.none { it.distance(location) < minDistance }
    }

    /*
     * =========================
     * ゲームタイマー
     * =========================
     */
    private fun startGameTimer() {

        gameTask?.cancel()

        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            // 🔥 最終フェーズ後は時間切れで終了しない
            if (remainingGameSeconds > 0) {
                remainingGameSeconds--
            }

            scoreboardManager.updateAll(
                state,
                playerManager.getAlivePlayers().size,
                remainingGameSeconds,
                borderManager.getCurrentPhaseIndex(),
                borderManager.getTotalPhases(),
                borderManager.getPhaseState(),
                borderManager.getRemainingPhaseSeconds()
            )

        }, 20L, 20L)
    }

    /*
     * =========================
     * 勝利判定
     * =========================
     */
    fun handleDeath(player: Player) {
        forceEliminate(player)
    }

    fun isAlive(uuid: java.util.UUID): Boolean {
        return playerManager.isAlive(uuid)
    }

    private fun checkWinCondition() {

        val alive = playerManager.getAlivePlayers()

        when (alive.size) {
            1 -> endGame(alive.first())
            0 -> endGame(null)
        }
    }

    private fun handleFinalPhaseFinished() {
        Bukkit.broadcastMessage("§c最終フェーズ突入！最後の1人になるまで戦え！")
    }

    /*
     * =========================
     * 脱落処理
     * =========================
     */
    fun forceEliminate(player: Player) {

        if (state != GameState.RUNNING) return
        if (!playerManager.isAlive(player.uniqueId)) return

        playerManager.markDead(player.uniqueId)

        makeSpectator(player)

        Bukkit.broadcastMessage("§c${player.name} はゲームから脱落しました")

        checkWinCondition()
    }

    private fun makeSpectator(player: Player) {

        player.gameMode = GameMode.SPECTATOR
        player.allowFlight = true
        player.isFlying = true

        player.health = player.maxHealth
        player.foodLevel = 20
        player.fireTicks = 0

        player.activePotionEffects.forEach {
            player.removePotionEffect(it.type)
        }
    }

    /*
     * =========================
     * 終了処理
     * =========================
     */
    fun endGame(winner: GamePlayer?) {

        if (state == GameState.ENDING) return

        state = GameState.ENDING

        gameTask?.cancel()
        borderManager.stop()

        Bukkit.broadcastMessage(
            if (winner?.player != null)
                "§6勝者: §a${winner.player!!.name}"
            else
                "§6勝者なし"
        )

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            resetGame()
        }, 100L)
    }

    private fun resetGame() {

        borderManager.reset()
        playerManager.clear()

        Bukkit.getOnlinePlayers().forEach { player ->
            player.gameMode = GameMode.SURVIVAL
            player.allowFlight = false
            player.isFlying = false
            player.health = player.maxHealth
            player.foodLevel = 20
            player.fireTicks = 0
        }

        state = GameState.WAITING
    }
}