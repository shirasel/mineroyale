package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.config.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.min
import kotlin.random.Random

class BorderManager(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {

    private val world get() = configManager.gameWorld
    private val border get() = world.worldBorder

    private var shrinkTask: BukkitTask? = null
    private var waitTask: BukkitTask? = null
    private var moveTask: BukkitTask? = null
    private var damageTask: BukkitTask? = null
    private var phaseCountdownTask: BukkitTask? = null
    private var graceTask: BukkitTask? = null

    private var currentCenterX = 0.0
    private var currentCenterZ = 0.0

    private var currentPhaseIndex = 0
    private var remainingPhaseSeconds = 0
    private var phaseState = "待機中"

    private var pvpEnabled = false

    private val outsideTime = mutableMapOf<Player, Int>()

    /*
     * =========================
     * 初期化
     * =========================
     */
    fun initialize() {

        stop()

        val startRadius = configManager.startSize / 2
        val worldLimit = configManager.randomCenterRange
        val max = worldLimit - startRadius

        currentCenterX = Random.nextDouble(-max, max)
        currentCenterZ = Random.nextDouble(-max, max)

        border.setCenter(currentCenterX, currentCenterZ)
        border.size = configManager.startSize

        startBorderDamage()
        startInitialGracePeriod()
    }

    /*
     * =========================
     * PvPグレース
     * =========================
     */
    private fun startInitialGracePeriod() {

        pvpEnabled = false
        Bukkit.broadcastMessage("§ePvPは30秒後に有効になります")

        graceTask?.cancel()

        graceTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {

            pvpEnabled = true
            Bukkit.broadcastMessage("§cPvPが有効になりました！")

            world.players.forEach {
                it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
            }

        }, 30 * 20L)
    }

    fun isPvpEnabled() = pvpEnabled

    /*
     * =========================
     * 安全スポーン
     * =========================
     */
    fun generateRandomSpawnLocations(players: List<Player>): Map<Player, Location> {

        val result = mutableMapOf<Player, Location>()
        val usedLocations = mutableListOf<Location>()

        val radius = border.size / 2 - 20
        val minDistance = configManager.minSpawnDistance

        for (player in players) {

            var location: Location
            var attempts = 0

            do {

                val x = currentCenterX + Random.nextDouble(-radius, radius)
                val z = currentCenterZ + Random.nextDouble(-radius, radius)

                val y = world.getHighestBlockYAt(x.toInt(), z.toInt())
                location = Location(world, x, (y + 1).toDouble(), z)

                attempts++

                if (!isSafeSpawn(location)) continue

                if (usedLocations.none { it.distance(location) < minDistance }) break

            } while (attempts < 300)

            usedLocations.add(location)
            result[player] = location
        }

        return result
    }

    private fun isSafeSpawn(loc: Location): Boolean {

        val block = loc.clone().subtract(0.0, 1.0, 0.0).block

        if (block.type == Material.WATER) return false
        if (block.type == Material.LAVA) return false
        if (block.type == Material.AIR) return false

        return true
    }

    /*
     * =========================
     * フェーズ制御
     * =========================
     */
    fun runPhases(onComplete: () -> Unit) {
        runPhase(configManager.borderPhases, 0, onComplete)
    }

    private fun runPhase(
        phases: List<ConfigManager.BorderPhase>,
        index: Int,
        onComplete: () -> Unit
    ) {

        if (index >= phases.size) {

            if (configManager.enableFinalMove) {
                startFinalMove()
            }

            onComplete()
            return
        }

        currentPhaseIndex = index
        val phase = phases[index]

        announcePhase(index, phase)

        if (phase.wait > 0) {

            phaseState = "待機中"
            startPhaseCountdown(phase.wait)

            waitTask?.cancel()
            waitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                startShrinkPhase(phases, index, onComplete)
            }, phase.wait * 20L)

        } else {
            startShrinkPhase(phases, index, onComplete)
        }
    }

    private fun announcePhase(index: Int, phase: ConfigManager.BorderPhase) {

        val phaseNumber = index + 1

        Bukkit.broadcastMessage("§6========== フェーズ $phaseNumber ==========")

        if (phase.wait > 0) {
            Bukkit.broadcastMessage("§e${phase.wait}秒間の待機時間")
        }

        Bukkit.broadcastMessage("§cボーダーが §f${phase.size} §cまで縮小")
        Bukkit.broadcastMessage("§7収縮時間: §f${phase.duration}秒")

        val title = net.kyori.adventure.title.Title.title(
            net.kyori.adventure.text.Component.text("フェーズ $phaseNumber"),
            net.kyori.adventure.text.Component.text("ボーダー縮小開始"),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(300),
                java.time.Duration.ofSeconds(3),
                java.time.Duration.ofMillis(700)
            )
        )

        world.players.forEach {

            it.showTitle(title)

            it.playSound(
                it.location,
                Sound.BLOCK_BELL_USE,
                1f,
                1f
            )
        }
    }

    private fun startShrinkPhase(
        phases: List<ConfigManager.BorderPhase>,
        index: Int,
        onComplete: () -> Unit
    ) {

        val phase = phases[index]

        phaseState = "縮小中"
        startPhaseCountdown(phase.duration)

        startShrink(border.size, phase.size, phase.duration.toLong()) {
            runPhase(phases, index + 1, onComplete)
        }
    }

    private fun startShrink(
        start: Double,
        end: Double,
        seconds: Long,
        onFinish: () -> Unit
    ) {

        shrinkTask?.cancel()

        val ticks = seconds * 20
        val diff = start - end
        val perTick = diff / ticks

        var current = start
        var count = 0L

        shrinkTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (count >= ticks) {
                border.size = end
                shrinkTask?.cancel()
                onFinish()
                return@Runnable
            }

            current -= perTick
            border.size = current
            count++

        }, 0L, 1L)
    }

    /*
     * =========================
     * 最終円移動
     * =========================
     */
    private fun startFinalMove() {

        val range = configManager.finalMoveRange
        val duration = configManager.finalMoveDuration

        if (range <= 0 || duration <= 0) return

        val targetX = currentCenterX + Random.nextDouble(-range, range)
        val targetZ = currentCenterZ + Random.nextDouble(-range, range)

        border.setCenter(targetX, targetZ)

        Bukkit.broadcastMessage("§c最終円が移動しています！")
    }

    /*
     * =========================
     * カウントダウン
     * =========================
     */
    private fun startPhaseCountdown(seconds: Int) {

        phaseCountdownTask?.cancel()
        remainingPhaseSeconds = seconds

        phaseCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (remainingPhaseSeconds <= 0) {
                phaseCountdownTask?.cancel()
                return@Runnable
            }

            remainingPhaseSeconds--

        }, 20L, 20L)
    }

    /*
     * =========================
     * ボーダーダメージ
     * =========================
     */
    private fun startBorderDamage() {

        damageTask?.cancel()

        damageTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            world.players.forEach { player ->

                if (isOutsideBorder(player)) {

                    val time = outsideTime.getOrDefault(player, 0) + 1
                    outsideTime[player] = time

                    val damage = min(
                        configManager.enhancedBaseDamage +
                                configManager.enhancedIncreasePerSecond * time,
                        configManager.enhancedMaxDamage
                    )

                    player.damage(damage)

                } else {
                    outsideTime.remove(player)
                }
            }

        }, 20L, 20L)
    }

    private fun isOutsideBorder(player: Player): Boolean {

        val dx = player.location.x - currentCenterX
        val dz = player.location.z - currentCenterZ

        val distanceSquared = dx * dx + dz * dz
        val radius = border.size / 2

        return distanceSquared > radius * radius
    }

    fun stop() {

        shrinkTask?.cancel()
        waitTask?.cancel()
        moveTask?.cancel()
        damageTask?.cancel()
        phaseCountdownTask?.cancel()
        graceTask?.cancel()

        outsideTime.clear()
    }

    fun reset() {

        stop()

        border.setCenter(0.0, 0.0)
        border.size = 29999984.0
    }

    fun getCurrentPhaseIndex() = currentPhaseIndex + 1
    fun getTotalPhases() = configManager.borderPhases.size
    fun getRemainingPhaseSeconds() = remainingPhaseSeconds
    fun getPhaseState() = phaseState
}