package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.config.ConfigManager
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.abs
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

    private var currentPhaseIndex = 0
    private var remainingPhaseSeconds = 0
    private var phaseState = "待機中"

    private var pvpEnabled = false
    private val outsideTime = mutableMapOf<UUID, Int>()

    private var gameEndTime: Long = 0

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

        val centerX = Random.nextDouble(-max, max)
        val centerZ = Random.nextDouble(-max, max)

        border.setCenter(centerX, centerZ)
        border.size = configManager.startSize

        calculateGameEndTime()

        startBorderDamage()
        startInitialGracePeriod()
    }

    private fun calculateGameEndTime() {

        var totalSeconds = 0

        configManager.borderPhases.forEach {
            totalSeconds += it.wait
            totalSeconds += it.duration
        }

        gameEndTime = System.currentTimeMillis() + (totalSeconds * 1000)
    }

    fun getRemainingGameSeconds(): Int {

        val remaining = (gameEndTime - System.currentTimeMillis()) / 1000
        return remaining.coerceAtLeast(0).toInt()
    }

    /*
     * =========================
     * PvPグレース
     * =========================
     */
    private fun startInitialGracePeriod() {

        pvpEnabled = false
        Bukkit.broadcast(Component.text("§ePvPは30秒後に有効になります"))

        graceTask?.cancel()

        graceTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {

            pvpEnabled = true
            Bukkit.broadcast(Component.text("§cPvPが有効になりました！"))

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

        val center = border.center
        val radius = border.size / 2 - 1

        val minDistance = configManager.minSpawnDistance
        val minDistanceSq = minDistance * minDistance

        for (player in players) {

            var location: Location? = null
            var attempts = 0

            while (attempts < 50) {

                val x = center.x + Random.nextDouble(-radius, radius)
                val z = center.z + Random.nextDouble(-radius, radius)

                val y = world.getHighestBlockYAt(x.toInt(), z.toInt())
                val candidate = Location(world, x, (y + 1).toDouble(), z)

                attempts++

                if (!isSafeSpawn(candidate)) continue
                if (usedLocations.any { it.distanceSquared(candidate) < minDistanceSq }) continue

                location = candidate
                break
            }

            if (location == null) {

                val y = world.getHighestBlockYAt(center.x.toInt(), center.z.toInt())
                location = Location(world, center.x, (y + 2).toDouble(), center.z)
            }

            usedLocations.add(location)
            result[player] = location
        }

        return result
    }

    private fun isSafeSpawn(loc: Location): Boolean {

        val ground = loc.clone().subtract(0.0, 1.0, 0.0).block.type

        if (ground == Material.LAVA) return false
        if (ground == Material.WATER) return false
        if (ground == Material.CACTUS) return false
        if (ground == Material.FIRE) return false
        if (ground == Material.CAMPFIRE) return false

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

        Bukkit.broadcast(Component.text("§6========== フェーズ $phaseNumber =========="))

        if (phase.wait > 0) {
            Bukkit.broadcast(Component.text("§e${phase.wait}秒間の待機時間"))
        }

        Bukkit.broadcast(Component.text("§cボーダーが §f${phase.size} §cまで縮小"))
        Bukkit.broadcast(Component.text("§7収縮時間: §f${phase.duration}秒"))
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

        if (seconds <= 0) {
            border.size = end
            onFinish()
            return
        }

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
     * 最終円移動（滑らか）
     * =========================
     */
    private fun startFinalMove() {

        val range = configManager.finalMoveRange
        val duration = configManager.finalMoveDuration

        if (range <= 0 || duration <= 0) return

        val start = border.center

        val targetX = start.x + Random.nextDouble(-range, range)
        val targetZ = start.z + Random.nextDouble(-range, range)

        val ticks = duration * 20
        val moveX = (targetX - start.x) / ticks
        val moveZ = (targetZ - start.z) / ticks

        var count = 0

        moveTask?.cancel()

        moveTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (count >= ticks) {
                moveTask?.cancel()
                return@Runnable
            }

            val center = border.center
            border.setCenter(center.x + moveX, center.z + moveZ)

            count++

        }, 0L, 1L)

        Bukkit.broadcast(Component.text("§c最終円が移動しています！"))
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

            val center = border.center
            val radius = border.size / 2
            val damageRadius = radius + 1

            world.players.forEach { player ->

                val dx = abs(player.location.x - center.x)
                val dz = abs(player.location.z - center.z)

                val outside = dx > damageRadius || dz > damageRadius

                val uuid = player.uniqueId

                if (!outside) {
                    outsideTime.remove(uuid)
                    return@forEach
                }

                val time = outsideTime.getOrDefault(uuid, 0) + 2
                outsideTime[uuid] = time

                val damage = kotlin.math.min(
                    configManager.enhancedBaseDamage +
                            configManager.enhancedIncreasePerSecond * time,
                    configManager.enhancedMaxDamage
                )

                player.damage(damage)
            }

        }, 40L, 40L)
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