package me.shirasemaru.mineroyale12111.service.border

import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.config.PhaseSettings
import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.game.PhaseState
import me.shirasemaru.mineroyale12111.service.game.MessageService
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random

class BorderService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val messageService: MessageService,
    private val onPvpStateChanged: (Boolean) -> Unit
) {

    private var shrinkTask: BukkitTask? = null
    private var waitTask: BukkitTask? = null
    private var moveTask: BukkitTask? = null
    private var phaseCountdownTask: BukkitTask? = null
    private var graceTask: BukkitTask? = null

    private var pvpEnabled = false
    private var gameEndTime: Long = 0

    fun initialize(session: GameSession, world: World, border: WorldBorder) {
        stop()

        session.currentPhase = 0
        session.totalPhases = configManager.borderSettings.phases.size
        session.phaseState = PhaseState.IDLE.displayName
        session.remainingPhaseSeconds = 0

        val startRadius = configManager.worldSettings.initialBorderSize / 2
        val worldLimit = configManager.worldSettings.randomCenterRange
        val max = worldLimit - startRadius

        val centerX = Random.nextDouble(-max, max)
        val centerZ = Random.nextDouble(-max, max)

        border.setCenter(centerX, centerZ)
        border.size = configManager.worldSettings.initialBorderSize
        border.warningDistance = configManager.borderSettings.warningDistance
        border.setWarningTimeTicks(configManager.borderSettings.warningTime * 20)

        calculateGameEndTime()
        updateRemainingGameSeconds(session)
        startInitialGracePeriod(world)
    }

    fun runPhases(session: GameSession, border: WorldBorder, onComplete: () -> Unit) {
        runPhase(session, border, configManager.borderSettings.phases, 0, onComplete)
    }

    fun stop() {
        shrinkTask?.cancel()
        waitTask?.cancel()
        moveTask?.cancel()
        phaseCountdownTask?.cancel()
        graceTask?.cancel()
    }

    fun reset(session: GameSession, border: WorldBorder) {
        stop()
        border.setCenter(0.0, 0.0)
        border.size = 29999984.0
        session.currentPhase = 0
        session.totalPhases = 0
        session.phaseState = PhaseState.IDLE.displayName
        session.remainingPhaseSeconds = 0
        session.remainingGameSeconds = 0
        pvpEnabled = false
        onPvpStateChanged(false)
    }

    fun isPvpEnabled(): Boolean = pvpEnabled

    private fun calculateGameEndTime() {
        var totalSeconds = 0

        configManager.borderSettings.phases.forEach {
            totalSeconds += it.waitSeconds
            totalSeconds += it.durationSeconds
        }

        if (configManager.borderSettings.finalPhase.enabled) {
            totalSeconds += configManager.borderSettings.finalPhase.moveDurationSeconds
        }

        gameEndTime = System.currentTimeMillis() + (totalSeconds * 1000L)
    }

    private fun updateRemainingGameSeconds(session: GameSession) {
        val remaining = (gameEndTime - System.currentTimeMillis()) / 1000
        session.remainingGameSeconds = remaining.coerceAtLeast(0).toInt()
    }

    private fun startInitialGracePeriod(world: World) {
        pvpEnabled = false
        onPvpStateChanged(false)
        messageService.broadcastPvpGracePeriod(configManager.gameSettings.initialPvpGraceSeconds)

        graceTask?.cancel()
        graceTask = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            pvpEnabled = true
            onPvpStateChanged(true)
            messageService.broadcastPvpEnabled(world.players)
        }, configManager.gameSettings.initialPvpGraceSeconds * 20L)
    }

    private fun runPhase(
        session: GameSession,
        border: WorldBorder,
        phases: List<PhaseSettings>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (index >= phases.size) {
            if (configManager.borderSettings.finalPhase.enabled) {
                startFinalMove(session, border)
            } else {
                onComplete()
            }
            return
        }

        val phase = phases[index]
        session.currentPhase = index + 1
        session.totalPhases = phases.size
        messageService.broadcastBorderPhase(index + 1, phase.waitSeconds, phase.targetSize, phase.durationSeconds)

        if (phase.waitSeconds > 0) {
            session.phaseState = PhaseState.PREPARING.displayName
            startPhaseCountdown(session, phase.waitSeconds)

            waitTask?.cancel()
            waitTask = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                startShrinkPhase(session, border, phases, index, onComplete)
            }, phase.waitSeconds * 20L)
        } else {
            startShrinkPhase(session, border, phases, index, onComplete)
        }
    }

    private fun startShrinkPhase(
        session: GameSession,
        border: WorldBorder,
        phases: List<PhaseSettings>,
        index: Int,
        onComplete: () -> Unit
    ) {
        val phase = phases[index]
        session.phaseState = PhaseState.SHRINKING.displayName
        startPhaseCountdown(session, phase.durationSeconds)

        startShrink(session, border, border.size, phase.targetSize, phase.durationSeconds.toLong()) {
            runPhase(session, border, phases, index + 1, onComplete)
        }
    }

    private fun startShrink(
        session: GameSession,
        border: WorldBorder,
        start: Double,
        end: Double,
        seconds: Long,
        onFinish: () -> Unit
    ) {
        shrinkTask?.cancel()

        if (seconds <= 0) {
            border.size = end
            updateRemainingGameSeconds(session)
            onFinish()
            return
        }

        val ticks = seconds * 20
        val diff = start - end
        val perTick = diff / ticks

        var current = start
        var count = 0L

        shrinkTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (count >= ticks) {
                border.size = end
                shrinkTask?.cancel()
                updateRemainingGameSeconds(session)
                onFinish()
                return@Runnable
            }

            current -= perTick
            border.size = current
            count++
        }, 0L, 1L)
    }

    private fun startFinalMove(session: GameSession, border: WorldBorder) {
        val range = configManager.borderSettings.finalPhase.moveRange
        val duration = configManager.borderSettings.finalPhase.moveDurationSeconds

        if (range <= 0 || duration <= 0) {
            return
        }

        val ticks = duration * 20
        var count = 0
        var moveX = 0.0
        var moveZ = 0.0
        moveTask?.cancel()
        session.phaseState = PhaseState.FINAL_MOVING.displayName

        fun chooseNextTarget() {
            val start = border.center
            val targetX = start.x + Random.nextDouble(-range, range)
            val targetZ = start.z + Random.nextDouble(-range, range)
            moveX = (targetX - start.x) / ticks
            moveZ = (targetZ - start.z) / ticks
            count = 0
            startPhaseCountdown(session, duration)
        }

        chooseNextTarget()

        moveTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (count >= ticks) {
                updateRemainingGameSeconds(session)
                chooseNextTarget()
                return@Runnable
            }

            val center = border.center
            border.setCenter(center.x + moveX, center.z + moveZ)
            count++
        }, 0L, 1L)

        messageService.broadcastFinalMoveStarted()
    }

    private fun startPhaseCountdown(session: GameSession, seconds: Int) {
        phaseCountdownTask?.cancel()
        session.remainingPhaseSeconds = seconds
        updateRemainingGameSeconds(session)

        phaseCountdownTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (session.remainingPhaseSeconds <= 0) {
                phaseCountdownTask?.cancel()
                return@Runnable
            }

            session.remainingPhaseSeconds--
            updateRemainingGameSeconds(session)
        }, 20L, 20L)
    }
}
