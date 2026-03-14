package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.Mineroyale12111
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.scheduler.BukkitTask
import java.time.Duration
import java.util.UUID

class GameManager(
    private val plugin: Mineroyale12111,
    private val configManager: ConfigManager,
    private val scoreboardManager: ScoreboardManager
) {

    private var state = GameState.WAITING

    private val alivePlayers = mutableSetOf<UUID>()
    private val spectators = mutableSetOf<UUID>()

    private val borderManager = BorderManager(plugin, configManager)

    private var countdownTask: BukkitTask? = null
    private var scoreboardTask: BukkitTask? = null
    private var compassTask: BukkitTask? = null

    fun getState() = state
    fun isRunning() = state == GameState.RUNNING
    fun isPvpEnabled() = borderManager.isPvpEnabled()

    /*
     * ========================================================
     * ゲーム開始
     * ========================================================
     */

    fun startGame() {

        if (state != GameState.WAITING) return

        val players = Bukkit.getOnlinePlayers().toList()

        if (players.size < configManager.minPlayers) {
        plugin.logger.info("参加人数不足")
        return
        }

        startCountdown(players)
    }

    private fun startCountdown(players: List<Player>) {

        var time = configManager.countdownSeconds

        countdownTask?.cancel()

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (time <= 0) {

                Bukkit.broadcast(Component.text("§aゲーム開始！"))
                countdownTask?.cancel()
                startMatch(players)
                return@Runnable
            }

            if (time <= 5 || time % 10 == 0) {

                Bukkit.broadcast(Component.text("§eゲーム開始まで §c${time}秒"))

                players.forEach {
                    it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                }
            }

            time--

        }, 0L, 20L)
    }

    private fun startMatch(players: List<Player>) {

        state = GameState.RUNNING

        alivePlayers.clear()
        spectators.clear()

        players.forEach {
            alivePlayers.add(it.uniqueId)
        }

        borderManager.initialize()

        setupPlayers(players)

        borderManager.runPhases {
            endGame(null)
        }

        startScoreboardTask()
        startCompassTracking()

        plugin.logger.info("Mineroyale: ゲーム開始")
    }

    /*
     * ========================================================
     * ゲーム終了
     * ========================================================
     */

    fun endGame(winner: Player?) {

        if (state != GameState.RUNNING) return

        state = GameState.WAITING

        stopScoreboardTask()
        stopCompass()

        borderManager.stop()
        borderManager.reset()

        if (winner != null && winner.isOnline) {

            teleportAllToWinner(winner)
            playVictoryEffect(winner)

        } else {

            Bukkit.broadcast(Component.text("§cゲーム終了"))
            resetGame()
        }
    }

    /*
     * ========================================================
     */

    private fun teleportAllToWinner(winner: Player) {

        if (!winner.isOnline) return

        val location = winner.location.clone().add(0.0, 1.0, 0.0)

        Bukkit.getOnlinePlayers().forEach {
            it.teleport(location)
        }
    }

    /*
     * ========================================================
     */

    private fun playVictoryEffect(winner: Player) {

        if (!winner.isOnline) return

        Bukkit.broadcast(Component.text("§6${winner.name} が勝利しました！"))

        val title = Title.title(
            Component.text("VICTORY!"),
            Component.text("${winner.name} の勝利"),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(3),
                Duration.ofMillis(1000)
            )
        )

        Bukkit.getOnlinePlayers().forEach {
            it.showTitle(title)
        }

        repeat(3) { spawnFirework(winner) }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            resetGame()
        }, 100L)
    }

    private fun spawnFirework(winner: Player) {

        if (!winner.isOnline) return

        val firework = winner.world.spawn(winner.location, Firework::class.java)

        val meta = firework.fireworkMeta

        meta.addEffect(
            FireworkEffect.builder()
                .withColor(Color.ORANGE)
                .withFade(Color.YELLOW)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build()
        )

        meta.power = 1
        firework.fireworkMeta = meta
    }

    /*
     * ========================================================
     */

    private fun resetGame() {

        resetAllPlayers()

        scoreboardManager.clear()

        alivePlayers.clear()
        spectators.clear()
    }

    /*
     * ========================================================
     * プレイヤー死亡
     * ========================================================
     */

    fun handlePlayerDeath(player: Player) {

        if (state != GameState.RUNNING) return
        if (!alivePlayers.contains(player.uniqueId)) return

        alivePlayers.remove(player.uniqueId)
        spectators.add(player.uniqueId)

        setSpectator(player)
        updateSpectatorHeads()

        Bukkit.broadcast(
            Component.text("§7${player.name} が脱落しました §8(${alivePlayers.size}人残り)")
        )

        if (alivePlayers.size <= 1) {

            val winnerUUID = alivePlayers.firstOrNull()
            val winner = winnerUUID?.let { Bukkit.getPlayer(it) }

            if (winner == null && spectators.contains(player.uniqueId)) {
                endGame(player)
            } else {
                endGame(winner)
            }
        }
    }

    /*
     * ========================================================
     */

    private fun setSpectator(player: Player) {

        player.gameMode = GameMode.SPECTATOR
        player.inventory.clear()
    }

    /*
     * ========================================================
     */

    private fun updateSpectatorHeads() {

        spectators.forEach { spectatorUUID ->

            val spectator = getPlayer(spectatorUUID) ?: return@forEach
            spectator.inventory.clear()

            var slot = 0

            alivePlayers.forEach { aliveUUID ->

                val alive = getPlayer(aliveUUID) ?: return@forEach

                val skull = createPlayerHead(alive)
                spectator.inventory.setItem(slot++, skull)
            }
        }
    }

    private fun createPlayerHead(target: Player): ItemStack {

        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta

        meta.owningPlayer = target
        meta.displayName(Component.text("§e${target.name} を観戦"))

        item.itemMeta = meta
        return item
    }

    /*
     * ========================================================
     * コンパス追跡
     * ========================================================
     */

    private fun startCompassTracking() {

        compassTask?.cancel()

        compassTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (alivePlayers.size <= 1) return@Runnable

            alivePlayers.forEach { uuid ->

                val player = getPlayer(uuid) ?: return@forEach

                val nearest = alivePlayers
                    .filter { it != uuid }
                    .mapNotNull { getPlayer(it) }
                    .minByOrNull { it.location.distance(player.location) }

                nearest?.let {
                    player.compassTarget = it.location
                }
            }

        }, 0L, 40L)
    }

    private fun stopCompass() {

        compassTask?.cancel()
        compassTask = null
    }

    /*
     * ========================================================
     * スコアボード
     * ========================================================
     */

    private fun startScoreboardTask() {

        scoreboardTask?.cancel()

        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (state != GameState.RUNNING) return@Runnable

            scoreboardManager.updateAll(
                gameState = state,
                aliveCount = alivePlayers.size,
                remainingGameSeconds = borderManager.getRemainingGameSeconds(),
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

    /*
     * ========================================================
     */

    private fun setupPlayers(players: List<Player>) {

        val spawnMap = borderManager.generateRandomSpawnLocations(players)

        players.forEach { player ->

            player.gameMode = GameMode.SURVIVAL
            resetHealth(player)

            player.foodLevel = 20
            player.inventory.clear()

            spawnMap[player]?.let {
                player.teleport(it)
            }
        }
    }

    private fun resetAllPlayers() {

        Bukkit.getOnlinePlayers().forEach {

            it.gameMode = GameMode.ADVENTURE
            resetHealth(it)

            it.foodLevel = 20
            it.inventory.clear()
        }
    }

    private fun resetHealth(player: Player) {

        val attribute = player.getAttribute(Attribute.MAX_HEALTH)
        val maxHealth = attribute?.value ?: 20.0

        player.health = maxHealth
    }

    /*
     * ========================================================
     */

    fun reloadConfig() {
        configManager.reload()
    }

    fun isAlive(player: Player): Boolean {
        return alivePlayers.contains(player.uniqueId)
    }

    private fun getPlayer(uuid: UUID): Player? {
        return Bukkit.getPlayer(uuid)
    }
}