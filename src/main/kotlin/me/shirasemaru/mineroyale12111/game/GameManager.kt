package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.Mineroyale12111
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import java.time.Duration

class GameManager(
    private val plugin: Mineroyale12111,
    private val configManager: ConfigManager,
    private val scoreboardManager: ScoreboardManager
) {

    private var state = GameState.WAITING
    private val alivePlayers = mutableSetOf<Player>()

    private val borderManager = BorderManager(plugin, configManager)

    private var scoreboardTask: BukkitTask? = null

    private val spectators = mutableSetOf<Player>()

    /* ======================================================== */

    fun getState(): GameState = state
    fun isRunning(): Boolean = state == GameState.RUNNING

    /* ======================================================== */
    /* ゲーム開始 */
    /* ======================================================== */

    fun startGame() {
        if (state != GameState.WAITING) return

        val players = Bukkit.getOnlinePlayers().toList()

        if (players.size < configManager.minPlayers) {
            plugin.logger.info("参加人数不足")
            return
        }

        state = GameState.RUNNING
        alivePlayers.clear()
        alivePlayers.addAll(players)

        setupPlayers(players)

        // ★ ボーダー初期化
        borderManager.initialize()

        // ★ フェーズ開始
        borderManager.runPhases {
            // 全フェーズ終了時
            endGame(null)
        }

        startScoreboardTask()

        plugin.logger.info("Mineroyale: ゲーム開始")
    }

    /* ======================================================== */
    /* ゲーム終了 */
    /* ======================================================== */

    fun endGame(winner: Player?) {
        if (state != GameState.RUNNING) return

        state = GameState.WAITING

        stopScoreboardTask()
        borderManager.stop()
        borderManager.reset()

        if (winner != null) {

            // 全員勝者へテレポート
            teleportAllToWinner(winner)

            // 勝利演出開始
            playVictoryEffect(winner)

        } else {
            Bukkit.broadcastMessage("§cゲーム終了")
            resetGame()
        }

        plugin.logger.info("Mineroyale: ゲーム終了処理開始")
    }

    // 勝者へのテレポート
    private fun teleportAllToWinner(winner: Player) {

        val location = winner.location.clone().add(0.0, 1.0, 0.0)

        for (player in Bukkit.getOnlinePlayers()) {
            player.teleport(location)
        }
    }

    // 勝者演出
    private fun playVictoryEffect(winner: Player) {

        Bukkit.broadcastMessage("§6${winner.name} が勝利しました！")

        // タイトル表示
        val title = Title.title(
            Component.text("VICTORY!"),
            Component.text("${winner.name} の勝利"),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(3),
                Duration.ofMillis(1000)
            )
        )

        for (player in Bukkit.getOnlinePlayers()) {
            player.showTitle(title)
        }

        // 花火発射（3回）
        repeat(3) {
            spawnFirework(winner)
        }

        // 5秒後にリセット
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            resetGame()
        }, 100L) // 100tick = 5秒
    }

    // 花火生成
    private fun spawnFirework(winner: Player) {

        val world = winner.world
        val firework = world.spawn(winner.location, org.bukkit.entity.Firework::class.java)

        val meta = firework.fireworkMeta
        meta.addEffect(
            org.bukkit.FireworkEffect.builder()
                .withColor(org.bukkit.Color.ORANGE)
                .withFade(org.bukkit.Color.YELLOW)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build()
        )
        meta.power = 1
        firework.fireworkMeta = meta
    }

    // リセット処理
    private fun resetGame() {

        resetAllPlayers()
        scoreboardManager.clear()

        alivePlayers.clear()
        spectators.clear()
    }

    /* ======================================================== */
    /* 強制終了 */
    /* ======================================================== */

    fun forceStopGame() {
        if (state != GameState.RUNNING) return
        endGame(null)
    }

    fun forceStopGameSilently() {
        if (state != GameState.RUNNING) return

        state = GameState.WAITING

        stopScoreboardTask()
        borderManager.stop()
        borderManager.reset()
        resetAllPlayers()
        scoreboardManager.clear()

        alivePlayers.clear()

        plugin.logger.info("Mineroyale: サイレント強制終了")
    }

    /* ======================================================== */
    /* プレイヤー死亡 */
    /* ======================================================== */

    fun handlePlayerDeath(player: Player) {
        if (state != GameState.RUNNING) return

        alivePlayers.remove(player)
        spectators.add(player)

        setSpectator(player)

        updateSpectatorHeads()

        if (alivePlayers.size <= 1) {
            val winner = alivePlayers.firstOrNull()
            endGame(winner)
        }
    }

    // 観戦モード
    private fun setSpectator(player: Player) {
        player.gameMode = GameMode.SPECTATOR
        player.inventory.clear()
    }

    // 生存者の頭を配布
    private fun updateSpectatorHeads() {
        for (spectator in spectators) {

            spectator.inventory.clear()

            var slot = 0

            for (alive in alivePlayers) {
                val skull = createPlayerHead(alive)
                spectator.inventory.setItem(slot++, skull)
            }
        }
    }

    // プレイヤー頭アイテム生成
    private fun createPlayerHead(target: Player): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta

        meta.owningPlayer = target
        meta.setDisplayName("§e${target.name} を観戦")

        item.itemMeta = meta
        return item
    }

    /* ======================================================== */
    /* スコアボード更新 */
    /* ======================================================== */

    private fun startScoreboardTask() {
        scoreboardTask?.cancel()

        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (state != GameState.RUNNING) return@Runnable

            scoreboardManager.updateAll(
                gameState = state,
                aliveCount = alivePlayers.size,
                remainingGameSeconds = 0,
                currentPhase = borderManager.getCurrentPhaseIndex(),
                totalPhases = borderManager.getTotalPhases(),
                phaseState = borderManager.getPhaseState(),
                remainingPhaseSeconds = borderManager.getRemainingPhaseSeconds()
            )

        }, 0L, 20L)
    }

    private fun stopScoreboardTask() {
        scoreboardTask?.cancel()
        scoreboardTask = null
    }

    /* ======================================================== */
    /* プレイヤー初期化 */
    /* ======================================================== */

    private fun setupPlayers(players: List<Player>) {
        for (player in players) {
            player.gameMode = GameMode.SURVIVAL
            resetHealth(player)
            player.foodLevel = 20
            player.inventory.clear()
        }
    }

    private fun resetAllPlayers() {
        for (player in Bukkit.getOnlinePlayers()) {
            player.gameMode = GameMode.ADVENTURE
            resetHealth(player)
            player.foodLevel = 20
            player.inventory.clear()
        }
    }

    private fun resetHealth(player: Player) {
        val attribute = player.getAttribute(Attribute.MAX_HEALTH)
        val maxHealth = attribute?.value ?: 20.0
        player.health = maxHealth
    }

    /* ======================================================== */

    fun reloadConfig() {
        configManager.reload()
    }
}