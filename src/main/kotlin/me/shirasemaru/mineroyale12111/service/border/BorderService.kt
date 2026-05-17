package me.shirasemaru.mineroyale12111.service.border

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.config.PhaseSettings
import me.shirasemaru.mineroyale12111.coroutines.waitTicks
import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.game.PhaseState
import me.shirasemaru.mineroyale12111.service.game.MessageService
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.plugin.java.JavaPlugin
import kotlin.random.Random

class BorderService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val messageService: MessageService,
    private val onPvpStateChanged: (Boolean) -> Unit,
    private val coroutineScope: CoroutineScope
) {

    private companion object {
        const val SHRINK_UPDATE_INTERVAL_TICKS = 2L
        const val FINAL_MOVE_UPDATE_INTERVAL_TICKS = 2L
    }

    private var phasesJob: Job? = null
    private var phaseCountdownJob: Job? = null
    private var graceJob: Job? = null

    private var pvpEnabled = false
    private var gameEndTime: Long = 0

    fun createInitialBorderPlan(): MatchBorderPlan {
        val startRadius = configManager.worldSettings.initialBorderSize / 2
        val worldLimit = configManager.worldSettings.randomCenterRange
        val max = worldLimit - startRadius

        return MatchBorderPlan(
            centerX = Random.nextDouble(-max, max),
            centerZ = Random.nextDouble(-max, max),
            size = configManager.worldSettings.initialBorderSize
        )
    }

    fun initialize(session: GameSession, world: World, border: WorldBorder) {
        initialize(session, world, border, createInitialBorderPlan())
    }

    fun initialize(session: GameSession, world: World, border: WorldBorder, plan: MatchBorderPlan) {
        stop()

        session.currentPhase = 0
        session.totalPhases = configManager.borderSettings.phases.size
        session.phaseState = PhaseState.IDLE.displayName
        session.remainingPhaseSeconds = 0

        border.setCenter(plan.centerX, plan.centerZ)
        border.size = plan.size
        border.warningDistance = configManager.borderSettings.warningDistance
        border.setWarningTimeTicks(configManager.borderSettings.warningTime * 20)

        calculateGameEndTime()
        updateRemainingGameSeconds(session)
        startInitialGracePeriod(world)
    }

    fun runPhases(session: GameSession, border: WorldBorder, onComplete: () -> Unit) {
        phasesJob?.cancel()
        phasesJob = coroutineScope.launch {
            runPhase(session, border, configManager.borderSettings.phases, 0, onComplete)
        }
    }

    fun stop() {
        phasesJob?.cancel()
        phasesJob = null
        phaseCountdownJob?.cancel()
        phaseCountdownJob = null
        graceJob?.cancel()
        graceJob = null
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

        graceJob?.cancel()
        graceJob = coroutineScope.launch {
            plugin.waitTicks(configManager.gameSettings.initialPvpGraceSeconds * 20L)
            if (!currentCoroutineContext().isActive) {
                return@launch
            }

            pvpEnabled = true
            onPvpStateChanged(true)
            messageService.broadcastPvpEnabled(world.players)
        }
    }

    private suspend fun runPhase(
        session: GameSession,
        border: WorldBorder,
        phases: List<PhaseSettings>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (!currentCoroutineContext().isActive) {
            return
        }

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
            plugin.waitTicks(phase.waitSeconds * 20L)
            if (!currentCoroutineContext().isActive) {
                return
            }
        }

        startShrinkPhase(session, border, phases, index, onComplete)
    }

    private suspend fun startShrinkPhase(
        session: GameSession,
        border: WorldBorder,
        phases: List<PhaseSettings>,
        index: Int,
        onComplete: () -> Unit
    ) {
        val phase = phases[index]
        session.phaseState = PhaseState.SHRINKING.displayName
        messageService.broadcastBorderShrinkStarted(index + 1, phase.targetSize, phase.durationSeconds)
        startPhaseCountdown(session, phase.durationSeconds)

        startShrink(session, border, border.size, phase.targetSize, phase.durationSeconds.toLong()) {
            runPhase(session, border, phases, index + 1, onComplete)
        }
    }

    private suspend fun startShrink(
        session: GameSession,
        border: WorldBorder,
        start: Double,
        end: Double,
        seconds: Long,
        onFinish: suspend () -> Unit
    ) {
        if (seconds <= 0) {
            border.size = end
            updateRemainingGameSeconds(session)
            onFinish()
            return
        }

        val totalTicks = seconds * 20
        val updateInterval = SHRINK_UPDATE_INTERVAL_TICKS
        val steps = (totalTicks + updateInterval - 1) / updateInterval
        val diff = start - end
        val perUpdate = diff / steps.toDouble()

        var current = start
        var count = 0L
        while (count < steps && currentCoroutineContext().isActive) {
            current -= perUpdate
            border.size = current
            count++
            if (count < steps) {
                plugin.waitTicks(updateInterval)
            }
        }

        if (!currentCoroutineContext().isActive) {
            return
        }

        border.size = end
        updateRemainingGameSeconds(session)
        onFinish()
    }

    private suspend fun startFinalMove(session: GameSession, border: WorldBorder) {
        val range = configManager.borderSettings.finalPhase.moveRange
        val duration = configManager.borderSettings.finalPhase.moveDurationSeconds

        if (range <= 0 || duration <= 0) {
            return
        }

        val totalTicks = duration * 20L
        val updateInterval = FINAL_MOVE_UPDATE_INTERVAL_TICKS
        val steps = ((totalTicks + updateInterval - 1) / updateInterval).toInt()
        session.phaseState = PhaseState.FINAL_MOVING.displayName
        messageService.broadcastFinalMoveStarted()

        while (currentCoroutineContext().isActive) {
            val start = border.center
            val targetX = start.x + Random.nextDouble(-range, range)
            val targetZ = start.z + Random.nextDouble(-range, range)
            val moveX = (targetX - start.x) / steps.toDouble()
            val moveZ = (targetZ - start.z) / steps.toDouble()

            startPhaseCountdown(session, duration)

            repeat(steps) { step ->
                if (!currentCoroutineContext().isActive) {
                    return
                }

                val center = border.center
                border.setCenter(center.x + moveX, center.z + moveZ)
                if (step < steps - 1) {
                    plugin.waitTicks(updateInterval)
                }
            }

            updateRemainingGameSeconds(session)
        }
    }

    private fun startPhaseCountdown(session: GameSession, seconds: Int) {
        phaseCountdownJob?.cancel()
        session.remainingPhaseSeconds = seconds
        updateRemainingGameSeconds(session)

        phaseCountdownJob = coroutineScope.launch {
            repeat(seconds) {
                plugin.waitTicks(20L)
                if (!currentCoroutineContext().isActive) {
                    return@launch
                }

                session.remainingPhaseSeconds--
                updateRemainingGameSeconds(session)
            }
        }
    }
}
