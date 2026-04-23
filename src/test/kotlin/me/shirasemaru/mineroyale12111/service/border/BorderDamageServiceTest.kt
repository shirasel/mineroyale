package me.shirasemaru.mineroyale12111.service.border

import io.mockk.every
import io.mockk.mockk
import me.shirasemaru.mineroyale12111.config.BorderSettings
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.config.EnhancedDamageSettings
import me.shirasemaru.mineroyale12111.config.FinalPhaseSettings
import me.shirasemaru.mineroyale12111.config.PhaseSettings
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

class BorderDamageServiceTest {

    @Test
    fun `start deals fixed damage only to players outside the border when enhanced damage is disabled`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val configManager = mockConfigManager(
            EnhancedDamageSettings(
                enabled = false,
                baseDamage = 1.0,
                increasePerSecond = 0.5,
                maxDamage = 10.0
            )
        )
        val service = BorderDamageService(plugin, configManager)
        val insideDamage = mutableListOf<Double>()
        val outsideDamage = mutableListOf<Double>()
        val insidePlayer = mockPlayer(
            uuid = UUID.nameUUIDFromBytes("inside".toByteArray()),
            x = 5.0,
            z = 5.0,
            damageLog = insideDamage
        )
        val outsidePlayer = mockPlayer(
            uuid = UUID.nameUUIDFromBytes("outside".toByteArray()),
            x = 20.0,
            z = 0.0,
            damageLog = outsideDamage
        )
        val world = mockWorld(listOf(insidePlayer, outsidePlayer))
        val border = mockBorder(size = 20.0)

        service.start(world, border) { listOf(insidePlayer, outsidePlayer) }
        service.observePlayer(insidePlayer, border, isAlive = true)
        service.observePlayer(outsidePlayer, border, isAlive = true)

        schedulerDriver.advanceTicks(40)

        assertEquals(emptyList(), insideDamage)
        assertEquals(listOf(1.0), outsideDamage)
    }

    @Test
    fun `enhanced damage increases over time and resets after returning inside the border`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val configManager = mockConfigManager(
            EnhancedDamageSettings(
                enabled = true,
                baseDamage = 1.0,
                increasePerSecond = 0.5,
                maxDamage = 10.0
            )
        )
        val service = BorderDamageService(plugin, configManager)
        val damageLog = mutableListOf<Double>()
        var location = Location(null, 20.0, 64.0, 0.0)
        val player = mockPlayer(
            uuid = UUID.nameUUIDFromBytes("enhanced".toByteArray()),
            locationProvider = { location },
            damageLog = damageLog
        )
        val world = mockWorld(listOf(player))
        val border = mockBorder(size = 20.0)

        service.start(world, border) { listOf(player) }
        service.observePlayer(player, border, isAlive = true)

        schedulerDriver.advanceTicks(40)
        schedulerDriver.advanceTicks(40)

        location = Location(null, 0.0, 64.0, 0.0)
        service.observePlayer(player, border, isAlive = true)
        schedulerDriver.advanceTicks(40)

        location = Location(null, 20.0, 64.0, 0.0)
        service.observePlayer(player, border, isAlive = true)
        schedulerDriver.advanceTicks(40)

        assertEquals(listOf(2.0, 3.0, 2.0), damageLog)
    }

    @Test
    fun `observePlayer only tracks alive players outside the border`() {
        val schedulerDriver = TestSchedulerDriver()
        val plugin = mockPlugin(schedulerDriver.scheduler)
        val configManager = mockConfigManager(
            EnhancedDamageSettings(
                enabled = false,
                baseDamage = 1.0,
                increasePerSecond = 0.5,
                maxDamage = 10.0
            )
        )
        val service = BorderDamageService(plugin, configManager)
        val damageLog = mutableListOf<Double>()
        val player = mockPlayer(
            uuid = UUID.nameUUIDFromBytes("spectator".toByteArray()),
            x = 20.0,
            z = 0.0,
            damageLog = damageLog
        )
        val border = mockBorder(size = 20.0)

        service.start(mockWorld(emptyList()), border) { emptyList() }
        service.observePlayer(player, border, isAlive = false)
        schedulerDriver.advanceTicks(40)

        assertEquals(emptyList(), damageLog)
    }

    private fun mockPlugin(scheduler: BukkitScheduler): JavaPlugin {
        val server = mockk<Server>()
        val plugin = mockk<JavaPlugin>()
        every { server.scheduler } returns scheduler
        every { plugin.server } returns server
        return plugin
    }

    private fun mockConfigManager(enhancedDamage: EnhancedDamageSettings): ConfigManager {
        val configManager = mockk<ConfigManager>()
        every { configManager.borderSettings } returns BorderSettings(
            warningDistance = 10,
            warningTime = 5,
            phases = listOf(PhaseSettings(waitSeconds = 1, durationSeconds = 1, targetSize = 80.0)),
            finalPhase = FinalPhaseSettings(enabled = false, moveRange = 0.0, moveDurationSeconds = 0),
            enhancedDamage = enhancedDamage
        )
        return configManager
    }

    private fun mockWorld(players: List<Player>): World {
        val world = mockk<World>()
        every { world.players } returns players
        return world
    }

    private fun mockBorder(size: Double): WorldBorder {
        val border = mockk<WorldBorder>()
        every { border.center } returns Location(null, 0.0, 0.0, 0.0)
        every { border.size } returns size
        return border
    }

    private fun mockPlayer(
        uuid: UUID,
        x: Double = 0.0,
        z: Double = 0.0,
        locationProvider: (() -> Location)? = null,
        damageLog: MutableList<Double>
    ): Player {
        val player = mockk<Player>()
        every { player.uniqueId } returns uuid
        every { player.location } answers { locationProvider?.invoke() ?: Location(null, x, 64.0, z) }
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
