package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.config.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.pow
import kotlin.math.sqrt
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

    private var currentCenterX = 0.0
    private var currentCenterZ = 0.0

    private var currentPhaseIndex = 0
    private var remainingPhaseSeconds = 0
    private var phaseState = "待機中"

    private val outsideTime = mutableMapOf<Player, Int>()

    /*
     * =========================
     * 初期化（ランダム中心）
     * =========================
     */
    fun initialize() {

        stop()

        val startRadius = configManager.startSize / 2
        val worldLimit = configManager.randomCenterRange  // ← configで指定する推奨

        val max = worldLimit - startRadius

        currentCenterX = Random.nextDouble(-max, max)
        currentCenterZ = Random.nextDouble(-max, max)

        border.setCenter(currentCenterX, currentCenterZ)
        border.size = configManager.startSize

        startBorderDamage()
    }

    /*
     * =========================
     * フェーズ実行
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

    private fun announcePhase(
        index: Int,
        phase: ConfigManager.BorderPhase
    ) {

        Bukkit.broadcastMessage("§6========== フェーズ ${index + 1} ==========")

        if (phase.wait > 0) {
            Bukkit.broadcastMessage("§e${phase.wait}秒間の待機時間")
        }

        Bukkit.broadcastMessage("§cボーダーが §f${phase.size} §cまで縮小")
        Bukkit.broadcastMessage("§7収縮時間: §f${phase.duration}秒")

        world.players.forEach {
            it.playSound(it.location, Sound.BLOCK_BELL_USE, 1.0f, 1.0f)
        }
    }

    private fun startShrinkPhase(
        phases: List<ConfigManager.BorderPhase>,
        index: Int,
        onComplete: () -> Unit
    ) {

        val phase = phases[index]
        val startSize = border.size
        val endSize = phase.size

        phaseState = "縮小中"
        startPhaseCountdown(phase.duration)

        if (index == phases.lastIndex && configManager.enableFinalMove) {
            smoothMoveCenter(
                configManager.finalMoveRange,
                configManager.finalMoveDuration.toLong()
            )
        }

        startShrink(startSize, endSize, phase.duration.toLong()) {
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
        if (ticks <= 0) {
            border.size = end
            onFinish()
            return
        }

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
     * 最終フェーズ中心移動
     * =========================
     */
    private fun smoothMoveCenter(range: Double, seconds: Long) {

        moveTask?.cancel()

        val targetX = currentCenterX + Random.nextDouble(-range, range)
        val targetZ = currentCenterZ + Random.nextDouble(-range, range)

        val ticks = seconds * 20
        if (ticks <= 0) return

        val stepX = (targetX - currentCenterX) / ticks
        val stepZ = (targetZ - currentCenterZ) / ticks

        var count = 0L

        moveTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (count >= ticks) {
                border.setCenter(targetX, targetZ)
                currentCenterX = targetX
                currentCenterZ = targetZ
                moveTask?.cancel()
                return@Runnable
            }

            currentCenterX += stepX
            currentCenterZ += stepZ
            border.setCenter(currentCenterX, currentCenterZ)

            count++

        }, 0L, 1L)
    }

    /*
     * =========================
     * ボーダー外ダメージ
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

        val distance = sqrt(dx.pow(2) + dz.pow(2))
        return distance > border.size / 2
    }

    /*
     * =========================
     * 停止・リセット
     * =========================
     */
    fun stop() {
        shrinkTask?.cancel()
        waitTask?.cancel()
        moveTask?.cancel()
        damageTask?.cancel()
        phaseCountdownTask?.cancel()
        outsideTime.clear()
    }

    fun reset() {

        stop()

        val world = configManager.gameWorld
        val border = world.worldBorder

        // バニラ初期値にリセット
        border.setCenter(0.0, 0.0)
        border.size = 29_999_984.0

        // ダメージ系も念のため初期化
        border.damageAmount = 0.2
        border.damageBuffer = 5.0

        // 警告初期値
        border.warningDistance = 5
    }

    fun getCurrentPhaseIndex() = currentPhaseIndex + 1
    fun getTotalPhases() = configManager.borderPhases.size
    fun getRemainingPhaseSeconds() = remainingPhaseSeconds
    fun getPhaseState() = phaseState

    fun getCurrentCenterX() = currentCenterX
    fun getCurrentCenterZ() = currentCenterZ
}