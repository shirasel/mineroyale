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

    private val managedTasks = mutableListOf<BukkitTask>()

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

        repeat(50) { // 最大50回試行

            val angle = Random.nextDouble() * 2 * PI
            val distance = sqrt(Random.nextDouble()) * radius

            val x = centerX + cos(angle) * distance
            val z = centerZ + sin(angle) * distance

            val blockX = x.toInt()
            val blockZ = z.toInt()

            val highestY = world.getHighestBlockYAt(blockX, blockZ)
            val groundBlock = world.getBlockAt(blockX, highestY - 1, blockZ)
            val feetBlock = world.getBlockAt(blockX, highestY, blockZ)
            val headBlock = world.getBlockAt(blockX, highestY + 1, blockZ)

            if (isSafeGround(groundBlock)
                && feetBlock.type.isAir
                && headBlock.type.isAir
                && isNotInHole(world, blockX, highestY, blockZ)
            ) {
                return Location(world, blockX + 0.5, highestY.toDouble(), blockZ + 0.5)
            }
        }

        // 最悪 fallback
        return Location(world, centerX, world.getHighestBlockYAt(centerX.toInt(), centerZ.toInt()).toDouble(), centerZ)
    }

    private fun isSafeGround(block: org.bukkit.block.Block): Boolean {

        if (!block.type.isSolid) return false

        return when (block.type) {
            Material.LAVA,
            Material.MAGMA_BLOCK,
            Material.CACTUS,
            Material.FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE -> false

            else -> true
        }
    }

    private fun isNotInHole(world: World, x: Int, y: Int, z: Int): Boolean {

        val north = world.getBlockAt(x, y, z - 1)
        val south = world.getBlockAt(x, y, z + 1)
        val east = world.getBlockAt(x + 1, y, z)
        val west = world.getBlockAt(x - 1, y, z)

        val solidCount = listOf(north, south, east, west)
            .count { it.type.isSolid }

        // 4方向全部壁なら1ブロック穴とみなす
        return solidCount < 4
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

        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (state != GameState.RUNNING) return@Runnable

            if (remainingGameSeconds > 0) {
                remainingGameSeconds--
            }

            if (remainingGameSeconds <= 0) {
                handleTimeUp()
                return@Runnable
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

        gameTask = task
        managedTasks.add(task)
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
            1 -> endGame(listOf(alive.first()))
            0 -> endGame(null)
        }
    }

    private fun handleFinalPhaseFinished() {
        Bukkit.broadcastMessage("§c最終フェーズ突入！")
    }

    private fun handleTimeUp() {

        if (state != GameState.RUNNING) return

        Bukkit.broadcastMessage("§c制限時間終了！")

        val alivePlayers = playerManager.getAlivePlayers()

        when (alivePlayers.size) {
            0 -> endGame(null)
            else -> endGame(alivePlayers) // 生存者全員勝利
        }
    }

    /*
     * =========================
     * 管理者強制終了
     * =========================
     */
    fun forceStopGame(executor: Player) {

        if (state != GameState.RUNNING) return

        state = GameState.WAITING

        cancelAllTasks()

        borderManager.stop()
        borderManager.reset()

        remainingGameSeconds = 0

        playerManager.clear()

        scoreboardManager.clearAll()

        val stopLocation = executor.location.clone().add(0.0, 0.5, 0.0)

        Bukkit.getOnlinePlayers().forEach { player ->

            player.gameMode = GameMode.SURVIVAL
            player.allowFlight = false
            player.isFlying = false

            player.health = player.maxHealth
            player.foodLevel = 20
            player.fireTicks = 0

            player.activePotionEffects.forEach {
                player.removePotionEffect(it.type)
            }

            player.inventory.clear()

            // 管理者の現在地へTP
            if (player != executor) {
                player.teleport(stopLocation)
            }
        }

        Bukkit.broadcastMessage(
            "§c管理者 §e${executor.name} §cによりゲームが強制終了されました。"
        )
    }

    private fun cancelAllTasks() {
        managedTasks.forEach { it.cancel() }
        managedTasks.clear()
        gameTask = null
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
    fun endGame(winners: List<GamePlayer>?) {

        if (state == GameState.ENDING) return

        state = GameState.ENDING

        gameTask?.cancel()
        borderManager.stop()

        when {
            winners.isNullOrEmpty() -> {
                Bukkit.broadcastMessage("§6勝者なし")
            }

            winners.size == 1 -> {
                Bukkit.broadcastMessage("§6勝者: §a${winners.first().player?.name}")
            }

            else -> {
                val names = winners.mapNotNull { it.player?.name }
                Bukkit.broadcastMessage("§6勝者（複数）: §a${names.joinToString(", ")}")
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            resetGame()
        }, 100L)
    }

    private fun resetGame() {

        state = GameState.WAITING

        gameTask?.cancel()
        gameTask = null

        remainingGameSeconds = 0  // ← これ追加

        borderManager.reset()
        playerManager.clear()

        Bukkit.getOnlinePlayers().forEach { player ->

            player.gameMode = GameMode.SURVIVAL
            player.allowFlight = false
            player.isFlying = false

            player.health = player.maxHealth
            player.foodLevel = 20
            player.fireTicks = 0

            player.activePotionEffects.forEach {
                player.removePotionEffect(it.type)
            }

            player.totalExperience = 0
            player.level = 0
            player.exp = 0f

            player.inventory.clear()
            player.inventory.helmet = null
            player.inventory.chestplate = null
            player.inventory.leggings = null
            player.inventory.boots = null
            player.inventory.setItemInOffHand(null)

            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        }

        Bukkit.broadcastMessage("§7ゲームがリセットされました")
    }
}
