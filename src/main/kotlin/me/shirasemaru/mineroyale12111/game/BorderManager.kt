package me.shirasemaru.mineroyale12111.game

import org.bukkit.Bukkit
import org.bukkit.WorldBorder
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class BorderManager(
    private val plugin: JavaPlugin,
    private val configManager: me.shirasemaru.mineroyale12111.config.ConfigManager
) {

    private val world = Bukkit.getWorlds()[0]
    private val border: WorldBorder = world.worldBorder

    private var currentTask: BukkitTask? = null

    fun initialize() {
        border.setCenter(configManager.borderCenterX, configManager.borderCenterZ)

        val phases = configManager.loadBorderPhases()
        if (phases.isNotEmpty()) {
            border.size = phases.first().startSize
        }
    }

    fun runPhases(phases: List<BorderPhase>, onComplete: () -> Unit) {
        runPhaseSequentially(phases, 0, onComplete)
    }

    private fun runPhaseSequentially(
        phases: List<BorderPhase>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (index >= phases.size) {
            onComplete()
            return
        }

        val phase = phases[index]

        startSmoothShrink(
            phase.startSize,
            phase.endSize,
            phase.durationSeconds
        ) {
            runPhaseSequentially(phases, index + 1, onComplete)
        }
    }

    private fun startSmoothShrink(
        startSize: Double,
        endSize: Double,
        durationSeconds: Long,
        onFinish: () -> Unit
    ) {
        currentTask?.cancel()

        val totalTicks = durationSeconds * 20
        val sizeDiff = startSize - endSize
        val shrinkPerTick = sizeDiff / totalTicks.toDouble()

        var currentSize = startSize
        var ticks = 0L

        currentTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {

            if (ticks >= totalTicks) {
                border.size = endSize
                currentTask?.cancel()
                onFinish()
                return@Runnable
            }

            currentSize -= shrinkPerTick
            border.size = currentSize

            ticks++

        }, 0L, 1L)
    }

    fun stop() {
        currentTask?.cancel()
    }

    fun reset() {
        stop()
        border.size = 1000.0
    }
}
