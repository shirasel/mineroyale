package me.shirasemaru.mineroyale.service.border

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.shirasemaru.mineroyale.config.BorderSettings
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.EnhancedDamageSettings
import me.shirasemaru.mineroyale.config.FinalPhaseSettings
import me.shirasemaru.mineroyale.config.GameSettings
import me.shirasemaru.mineroyale.config.PhaseSettings
import me.shirasemaru.mineroyale.config.WorldSettings
import me.shirasemaru.mineroyale.game.GameSession
import me.shirasemaru.mineroyale.game.PhaseState
import me.shirasemaru.mineroyale.service.game.MessageService
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.bukkit.Server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BorderServiceTest {

    @Test
    fun `initialize configures border and resets session state`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val configManager = mockConfigManager(
            borderSettings = BorderSettings(
                warningDistance = 12,
                warningTime = 7,
                phases = listOf(PhaseSettings(waitSeconds = 5, durationSeconds = 10, targetSize = 300.0)),
                finalPhase = FinalPhaseSettings(enabled = true, moveRange = 20.0, moveDurationSeconds = 15),
                enhancedDamage = EnhancedDamageSettings(enabled = false, baseDamage = 1.0, increasePerSecond = 0.5, maxDamage = 10.0)
            ),
            worldSettings = WorldSettings(
                name = "world",
                randomCenterRange = 1000.0,
                initialBorderSize = 400.0
            ),
            gameSettings = GameSettings(
                minPlayers = 2,
                maxPlayers = 20,
                countdownSeconds = 30,
                initialPvpGraceSeconds = 3,
                showPlayerLocatorBar = true,
                playerLocatorMaxAlivePlayers = 4,
                giveInitialCompass = true,
                giveEndCrystal = true,
                endCrystalGlowSeconds = 15,
                endCrystalItemName = "発光の岩",
                endCrystalItemDescription = "使用すると%seconds%秒間自分以外の生存者1名をランダムで発光させます。",
                hideNameTags = false,
                disableAdvancementAnnouncements = false,
                restrictBlockModificationOutsideBorder = false
            )
        )
        val messageService = mockk<MessageService>(relaxed = true)
        val pvpChanges = mutableListOf<Boolean>()
        val service = BorderService(plugin, configManager, messageService, { pvpChanges += it }, CoroutineScope(Dispatchers.Unconfined))
        val border = mockBorder(initialSize = 100.0)
        val world = mockWorld()
        val session = GameSession(
            currentPhase = 99,
            totalPhases = 99,
            phaseState = PhaseState.SHRINKING.displayName,
            remainingPhaseSeconds = 25
        )

        service.initialize(session, world, border)

        assertEquals(0, session.currentPhase)
        assertEquals(1, session.totalPhases)
        assertEquals(PhaseState.IDLE.displayName, session.phaseState)
        assertEquals(0, session.remainingPhaseSeconds)
        assertTrue(session.remainingGameSeconds in 29..30)
        assertEquals(400.0, border.size, 0.001)
        assertEquals(12, border.warningDistance)
        assertFalse(service.isPvpEnabled())
        assertEquals(listOf(false), pvpChanges)
        assertTrue(border.center.x in -800.0..800.0)
        assertTrue(border.center.z in -800.0..800.0)
        verify(exactly = 1) { messageService.broadcastPvpGracePeriod(3) }
    }

    @Test
    fun `initialize enables pvp after grace period ends`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val activePlayers = listOf(mockPlayer(), mockPlayer())
        val configManager = mockConfigManager(
            borderSettings = BorderSettings(
                warningDistance = 10,
                warningTime = 5,
                phases = listOf(PhaseSettings(waitSeconds = 1, durationSeconds = 1, targetSize = 80.0)),
                finalPhase = FinalPhaseSettings(enabled = false, moveRange = 0.0, moveDurationSeconds = 0),
                enhancedDamage = EnhancedDamageSettings(enabled = false, baseDamage = 1.0, increasePerSecond = 0.5, maxDamage = 10.0)
            ),
            gameSettings = GameSettings(
                minPlayers = 2,
                maxPlayers = 20,
                countdownSeconds = 30,
                initialPvpGraceSeconds = 2,
                showPlayerLocatorBar = true,
                playerLocatorMaxAlivePlayers = 4,
                giveInitialCompass = true,
                giveEndCrystal = true,
                endCrystalGlowSeconds = 15,
                endCrystalItemName = "発光の岩",
                endCrystalItemDescription = "使用すると%seconds%秒間自分以外の生存者1名をランダムで発光させます。",
                hideNameTags = false,
                disableAdvancementAnnouncements = false,
                restrictBlockModificationOutsideBorder = false
            ),
            worldSettings = WorldSettings(
                name = "world",
                randomCenterRange = 1000.0,
                initialBorderSize = 400.0
            )
        )
        val messageService = mockk<MessageService>(relaxed = true)
        val pvpChanges = mutableListOf<Boolean>()
        val service = BorderService(plugin, configManager, messageService, { pvpChanges += it }, CoroutineScope(Dispatchers.Unconfined))
        val border = mockBorder(initialSize = 100.0)
        val world = mockWorld(activePlayers)
        val session = GameSession()

        service.initialize(session, world, border)

        schedulerDriver.advanceTicks(39)
        assertFalse(service.isPvpEnabled())

        schedulerDriver.advanceTicks(1)

        assertTrue(service.isPvpEnabled())
        assertEquals(listOf(false, true), pvpChanges)
        verify(exactly = 1) { messageService.broadcastPvpEnabled(activePlayers) }
    }

    @Test
    fun `runPhases completes a configured phase and updates session state`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val configManager = mockConfigManager(
            borderSettings = BorderSettings(
                warningDistance = 10,
                warningTime = 5,
                phases = listOf(PhaseSettings(waitSeconds = 1, durationSeconds = 1, targetSize = 80.0)),
                finalPhase = FinalPhaseSettings(enabled = false, moveRange = 0.0, moveDurationSeconds = 0),
                enhancedDamage = EnhancedDamageSettings(enabled = false, baseDamage = 1.0, increasePerSecond = 0.5, maxDamage = 10.0)
            )
        )
        val messageService = mockk<MessageService>(relaxed = true)
        val service = BorderService(plugin, configManager, messageService, { }, CoroutineScope(Dispatchers.Unconfined))
        val border = mockBorder(initialSize = 100.0)
        val session = GameSession()
        var completed = false

        service.runPhases(session, border) { completed = true }

        assertEquals(1, session.currentPhase)
        assertEquals(1, session.totalPhases)
        assertEquals(PhaseState.PREPARING.displayName, session.phaseState)
        assertEquals(1, session.remainingPhaseSeconds)
        assertFalse(completed)

        schedulerDriver.advanceTicks(21)

        assertEquals(PhaseState.SHRINKING.displayName, session.phaseState)
        assertEquals(1, session.remainingPhaseSeconds)
        assertFalse(completed)

        schedulerDriver.advanceTicks(21)

        assertEquals(80.0, border.size, 0.001)
        assertEquals(0, session.remainingPhaseSeconds)
        assertTrue(completed)
        verify(exactly = 1) { messageService.broadcastBorderPhase(1, 1, 80.0, 1) }
    }

    @Test
    fun `runPhases keeps final move running by retargeting continuously`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val configManager = mockConfigManager(
            borderSettings = BorderSettings(
                warningDistance = 10,
                warningTime = 5,
                phases = listOf(PhaseSettings(waitSeconds = 0, durationSeconds = 1, targetSize = 50.0)),
                finalPhase = FinalPhaseSettings(enabled = true, moveRange = 10.0, moveDurationSeconds = 1),
                enhancedDamage = EnhancedDamageSettings(enabled = false, baseDamage = 1.0, increasePerSecond = 0.5, maxDamage = 10.0)
            )
        )
        val messageService = mockk<MessageService>(relaxed = true)
        val service = BorderService(plugin, configManager, messageService, { }, CoroutineScope(Dispatchers.Unconfined))
        val border = mockBorder(initialSize = 100.0, initialCenterX = 0.0, initialCenterZ = 0.0)
        val session = GameSession()
        var completed = false

        service.runPhases(session, border) { completed = true }

        schedulerDriver.advanceTicks(21)

        assertFalse(completed)
        assertEquals(PhaseState.FINAL_MOVING.displayName, session.phaseState)
        verify(exactly = 1) { messageService.broadcastFinalMoveStarted() }

        val firstCenter = border.center

        schedulerDriver.advanceTicks(21)

        assertFalse(completed)
        assertTrue(border.center.x != firstCenter.x || border.center.z != firstCenter.z)
        assertTrue(session.remainingPhaseSeconds in 0..1)
    }

    private fun mockPlugin(scheduler: BukkitScheduler): JavaPlugin {
        val server = mockk<Server>()
        val plugin = mockk<JavaPlugin>()
        every { server.scheduler } returns scheduler
        every { plugin.server } returns server
        return plugin
    }

    private fun mockConfigManager(
        borderSettings: BorderSettings,
        worldSettings: WorldSettings = WorldSettings(
            name = "world",
            randomCenterRange = 1000.0,
            initialBorderSize = 400.0
        ),
        gameSettings: GameSettings = GameSettings(
            minPlayers = 2,
            maxPlayers = 20,
            countdownSeconds = 30,
            initialPvpGraceSeconds = 45,
            showPlayerLocatorBar = true,
            playerLocatorMaxAlivePlayers = 4,
            giveInitialCompass = true,
            giveEndCrystal = true,
            endCrystalGlowSeconds = 15,
            endCrystalItemName = "発光の岩",
            endCrystalItemDescription = "使用すると%seconds%秒間自分以外の生存者1名をランダムで発光させます。",
            hideNameTags = false,
            disableAdvancementAnnouncements = false,
            restrictBlockModificationOutsideBorder = false
        )
    ): ConfigManager {
        val configManager = mockk<ConfigManager>()
        every { configManager.borderSettings } returns borderSettings
        every { configManager.worldSettings } returns worldSettings
        every { configManager.gameSettings } returns gameSettings
        return configManager
    }

    private fun mockWorld(players: List<Player> = emptyList()): World {
        val world = mockk<World>()
        every { world.players } returns players
        return world
    }

    private fun mockBorder(
        initialSize: Double,
        initialCenterX: Double = 0.0,
        initialCenterZ: Double = 0.0
    ): WorldBorder {
        var size = initialSize
        var centerX = initialCenterX
        var centerZ = initialCenterZ
        var warningDistance = 0

        val border = mockk<WorldBorder>()
        every { border.size } answers { size }
        every { border.size = any() } answers { size = firstArg() }
        every { border.center } answers { Location(null, centerX, 0.0, centerZ) }
        every { border.setCenter(any<Double>(), any<Double>()) } answers {
            centerX = firstArg()
            centerZ = secondArg()
        }
        every { border.warningDistance } answers { warningDistance }
        every { border.warningDistance = any() } answers { warningDistance = firstArg() }
        every { border.setWarningTimeTicks(any()) } just runs
        return border
    }

    private fun mockPlayer(): Player = mockk(relaxed = true)

    private class TestSchedulerDriver {
        private data class TaskEntry(
            val runnable: Runnable,
            var nextTick: Long,
            val period: Long?,
            var cancelled: Boolean = false
        )

        private var currentTick = 0L
        private val tasks = mutableListOf<TaskEntry>()

        val scheduler: BukkitScheduler = mockk {
            every { runTaskLater(any<JavaPlugin>(), any<Runnable>(), any<Long>()) } answers {
                schedule(
                    runnable = secondArg(),
                    delay = thirdArg(),
                    period = null
                )
            }

            every { runTaskTimer(any<JavaPlugin>(), any<Runnable>(), any<Long>(), any<Long>()) } answers {
                schedule(
                    runnable = secondArg(),
                    delay = thirdArg(),
                    period = arg(3)
                )
            }
        }

        fun advanceTicks(ticks: Int) {
            repeat(ticks) {
                currentTick++
                val dueTasks = tasks.filter { !it.cancelled && it.nextTick <= currentTick }.toList()
                dueTasks.forEach { entry ->
                    if (entry.cancelled) return@forEach

                    entry.runnable.run()

                    if (entry.cancelled) return@forEach

                    if (entry.period != null) {
                        entry.nextTick += entry.period
                    } else {
                        entry.cancelled = true
                    }
                }
            }
        }

        private fun schedule(runnable: Runnable, delay: Long, period: Long?): BukkitTask {
            val entry = TaskEntry(
                runnable = runnable,
                nextTick = currentTick + delay.coerceAtLeast(1L),
                period = period?.coerceAtLeast(1L)
            )
            val task = mockk<BukkitTask>()
            every { task.cancel() } answers { entry.cancelled = true }
            tasks += entry
            return task
        }
    }
}
