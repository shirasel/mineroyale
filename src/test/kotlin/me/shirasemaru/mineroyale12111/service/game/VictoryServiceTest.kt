package me.shirasemaru.mineroyale12111.service.game

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VictoryServiceTest {

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `playVictory teleports players in batches and finishes after a delay`() {
        mockkStatic(Bukkit::class)

        val schedulerDriver = TestSchedulerDriver()
        val scheduler = schedulerDriver.scheduler
        val server = mockk<Server>()
        val plugin = mockk<JavaPlugin>()
        val messageService = mockk<MessageService>()
        val world = mockk<World>()
        val winner = mockPlayer("winner", world)
        val others = listOf(
            mockPlayer("alpha", world),
            mockPlayer("bravo", world),
            mockPlayer("charlie", world),
            mockPlayer("delta", world),
            mockPlayer("echo", world)
        )
        val onlinePlayers = listOf(winner) + others
        var finished = false

        every { Bukkit.getScheduler() } returns scheduler
        every { Bukkit.getOnlinePlayers() } returns onlinePlayers.toSet()
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { messageService.broadcastVictory("winner") } just runs
        every { messageService.victoryTitle("winner") } returns mockk<Title>()
        every { world.spawn(any<Location>(), Firework::class.java) } returns mockFirework()

        val service = VictoryService(plugin, messageService)

        service.playVictory(winner) { finished = true }

        schedulerDriver.advanceTicks(1)

        verify(exactly = 1) { winner.teleport(any<Location>()) }
        verify(exactly = 1) { others[0].teleport(any<Location>()) }
        verify(exactly = 1) { others[1].teleport(any<Location>()) }
        verify(exactly = 1) { others[2].teleport(any<Location>()) }
        verify(exactly = 0) { others[3].teleport(any<Location>()) }
        verify(exactly = 0) { others[4].teleport(any<Location>()) }
        assertFalse(finished)

        schedulerDriver.advanceTicks(2)

        verify(exactly = 1) { others[3].teleport(any<Location>()) }
        verify(exactly = 1) { others[4].teleport(any<Location>()) }
        assertFalse(finished)

        schedulerDriver.advanceTicks(60)

        assertTrue(finished)
        verify(exactly = 1) { messageService.broadcastVictory("winner") }
        verify(exactly = 3) { world.spawn(any<Location>(), Firework::class.java) }
    }

    private fun mockPlayer(name: String, world: World): Player {
        val player = mockk<Player>()
        every { player.name } returns name
        every { player.world } returns world
        every { player.location } returns Location(world, 10.0, 64.0, 10.0)
        every { player.teleport(any<Location>()) } returns true
        every { player.showTitle(any<Title>()) } just runs
        return player
    }

    private fun mockFirework(): Firework {
        val firework = mockk<Firework>()
        val meta = mockk<org.bukkit.inventory.meta.FireworkMeta>()
        every { firework.fireworkMeta } returns meta
        every { firework.fireworkMeta = any() } just runs
        every { meta.addEffect(any()) } just runs
        every { meta.power = any() } just runs
        return firework
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
