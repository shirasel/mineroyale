package me.shirasemaru.mineroyale.service.border

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.shirasemaru.mineroyale.bootstrap.GameWorldProvider
import me.shirasemaru.mineroyale.config.BorderSettings
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.EnhancedDamageSettings
import me.shirasemaru.mineroyale.config.FinalPhaseSettings
import me.shirasemaru.mineroyale.config.GameSettings
import me.shirasemaru.mineroyale.config.PhaseSettings
import me.shirasemaru.mineroyale.config.SpawnSettings
import me.shirasemaru.mineroyale.config.WorldSettings
import me.shirasemaru.mineroyale.game.GameSession
import me.shirasemaru.mineroyale.game.PhaseState
import me.shirasemaru.mineroyale.service.game.MessageService
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BorderManagerTest {

    @Test
    fun `initialize wires border setup and outside damage service together`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val damageLog = mutableListOf<Double>()
        val outsidePlayer = mockPlayer(
            uuid = UUID.nameUUIDFromBytes("outside".toByteArray()),
            x = 20.0,
            z = 0.0,
            damageLog = damageLog
        )
        val border = mockBorder(initialSize = 20.0)
        val world = mockWorld(border, listOf(outsidePlayer))
        val configManager = mockConfigManager(world)
        val worldProvider = mockWorldProvider(world)
        val messageService = mockk<MessageService>(relaxed = true)
        val pvpChanges = mutableListOf<Boolean>()
        val manager = BorderManager(plugin, configManager, worldProvider, messageService, { pvpChanges += it }, { listOf(outsidePlayer) }, CoroutineScope(Dispatchers.Unconfined))
        val session = GameSession()

        manager.initialize(session)
        manager.observeBorderDamageTarget(outsidePlayer, isAlive = true)
        schedulerDriver.advanceTicks(40)

        assertEquals(0, session.currentPhase)
        assertEquals(1, session.totalPhases)
        assertEquals(PhaseState.IDLE.displayName, session.phaseState)
        assertEquals(100.0, border.size, 0.001)
        assertFalse(manager.isPvpEnabled())
        assertEquals(listOf(false), pvpChanges)
        assertEquals(listOf(1.0), damageLog)
        verify(exactly = 1) { messageService.broadcastPvpGracePeriod(3) }
    }

    @Test
    fun `reset restores border state and stops further damage ticks`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val damageLog = mutableListOf<Double>()
        val outsidePlayer = mockPlayer(
            uuid = UUID.nameUUIDFromBytes("outside".toByteArray()),
            x = 20.0,
            z = 0.0,
            damageLog = damageLog
        )
        val border = mockBorder(initialSize = 20.0)
        val world = mockWorld(border, listOf(outsidePlayer))
        val configManager = mockConfigManager(world)
        val worldProvider = mockWorldProvider(world)
        val messageService = mockk<MessageService>(relaxed = true)
        val manager = BorderManager(plugin, configManager, worldProvider, messageService, { }, { listOf(outsidePlayer) }, CoroutineScope(Dispatchers.Unconfined))
        val session = GameSession(
            currentPhase = 2,
            totalPhases = 4,
            phaseState = PhaseState.SHRINKING.displayName,
            remainingPhaseSeconds = 10,
            remainingGameSeconds = 20
        )

        manager.initialize(session)
        manager.observeBorderDamageTarget(outsidePlayer, isAlive = true)
        schedulerDriver.advanceTicks(40)

        manager.reset(session)
        schedulerDriver.advanceTicks(80)

        assertEquals(listOf(1.0), damageLog)
        assertEquals(0, session.currentPhase)
        assertEquals(0, session.totalPhases)
        assertEquals(PhaseState.IDLE.displayName, session.phaseState)
        assertEquals(0, session.remainingPhaseSeconds)
        assertEquals(0, session.remainingGameSeconds)
        assertFalse(manager.isPvpEnabled())
        assertEquals(29999984.0, border.size, 0.001)
        assertEquals(0.0, border.center.x, 0.001)
        assertEquals(0.0, border.center.z, 0.001)
    }

    private fun mockPlugin(scheduler: BukkitScheduler): JavaPlugin {
        val server = mockk<Server>()
        val plugin = mockk<JavaPlugin>()
        every { server.scheduler } returns scheduler
        every { plugin.server } returns server
        return plugin
    }

    private fun mockConfigManager(world: World): ConfigManager {
        val configManager = mockk<ConfigManager>()
        every { configManager.gameWorld } returns world
        every { configManager.worldSettings } returns WorldSettings(
            name = "world",
            randomCenterRange = 1000.0,
            initialBorderSize = 100.0
        )
        every { configManager.spawnSettings } returns SpawnSettings(
            minDistance = 50.0
        )
        every { configManager.gameSettings } returns GameSettings(
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
        every { configManager.borderSettings } returns BorderSettings(
            warningDistance = 10,
            warningTime = 5,
            phases = listOf(PhaseSettings(waitSeconds = 1, durationSeconds = 1, targetSize = 80.0)),
            finalPhase = FinalPhaseSettings(enabled = false, moveRange = 0.0, moveDurationSeconds = 0),
            enhancedDamage = EnhancedDamageSettings(
                enabled = false,
                baseDamage = 1.0,
                increasePerSecond = 0.5,
                maxDamage = 10.0
            )
        )
        return configManager
    }

    private fun mockWorldProvider(world: World): GameWorldProvider =
        mockk {
            every { require() } returns world
            every { find() } returns world
        }

    private fun mockWorld(border: WorldBorder, players: List<Player>): World {
        val world = mockk<World>()
        every { world.worldBorder } returns border
        every { world.players } returns players
        return world
    }

    private fun mockBorder(initialSize: Double): WorldBorder {
        var size = initialSize
        var centerX = 0.0
        var centerZ = 0.0
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

    private fun mockPlayer(
        uuid: UUID,
        x: Double,
        z: Double,
        damageLog: MutableList<Double>
    ): Player {
        val player = mockk<Player>()
        every { player.uniqueId } returns uuid
        every { player.location } returns Location(null, x, 64.0, z)
        every { player.damage(any()) } answers { damageLog += firstArg<Double>() }
        return player
    }

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
