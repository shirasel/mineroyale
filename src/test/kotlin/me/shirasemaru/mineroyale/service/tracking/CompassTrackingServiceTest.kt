package me.shirasemaru.mineroyale.service.tracking

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.GameSettings
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CompassTrackingServiceTest {

    @Test
    fun `start does not schedule locator updates when disabled in config`() {
        val scheduler = mockk<BukkitScheduler>(relaxed = true)
        val service = CompassTrackingService(
            plugin = mockPlugin(scheduler),
            configManager = mockConfigManager(showPlayerLocatorBar = false),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        service.start { emptyList() }

        verify(exactly = 0) { scheduler.runTaskLater(any<JavaPlugin>(), any<Runnable>(), any<Long>()) }
    }

    @Test
    fun `start schedules next locator update when enabled in config`() {
        val scheduler = mockk<BukkitScheduler>()
        every { scheduler.runTaskLater(any<JavaPlugin>(), any<Runnable>(), any<Long>()) } returns mockk<BukkitTask>(relaxed = true)

        val service = CompassTrackingService(
            plugin = mockPlugin(scheduler),
            configManager = mockConfigManager(showPlayerLocatorBar = true),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        service.start { emptyList() }

        verify(exactly = 1) { scheduler.runTaskLater(any<JavaPlugin>(), any<Runnable>(), 40L) }
    }

    @Test
    fun `locator updates are skipped when alive players exceed configured maximum`() {
        val scheduler = mockk<BukkitScheduler>()
        every { scheduler.runTaskLater(any<JavaPlugin>(), any<Runnable>(), any<Long>()) } returns mockk<BukkitTask>(relaxed = true)

        val service = CompassTrackingService(
            plugin = mockPlugin(scheduler),
            configManager = mockConfigManager(showPlayerLocatorBar = true, playerLocatorMaxAlivePlayers = 3),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        val world = mockk<World>()

        val players = listOf(
            mockPlayer("alpha", world, 0.0, 0.0),
            mockPlayer("bravo", world, 10.0, 0.0),
            mockPlayer("charlie", world, 20.0, 0.0),
            mockPlayer("delta", world, 30.0, 0.0)
        )

        service.start { players }

        players.forEach { player ->
            verify(exactly = 0) { player.compassTarget = any() }
        }
    }

    @Test
    fun `locator updates nearest target when alive players are within configured maximum`() {
        val scheduler = mockk<BukkitScheduler>()
        every { scheduler.runTaskLater(any<JavaPlugin>(), any<Runnable>(), any<Long>()) } returns mockk<BukkitTask>(relaxed = true)

        val service = CompassTrackingService(
            plugin = mockPlugin(scheduler),
            configManager = mockConfigManager(showPlayerLocatorBar = true, playerLocatorMaxAlivePlayers = 3),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        val world = mockk<World>()

        val alphaTarget = slot<Location>()
        val bravoTarget = slot<Location>()
        val charlieTarget = slot<Location>()
        val alpha = mockPlayer("alpha", world, 0.0, 0.0, alphaTarget)
        val bravo = mockPlayer("bravo", world, 10.0, 0.0, bravoTarget)
        val charlie = mockPlayer("charlie", world, 30.0, 0.0, charlieTarget)

        service.start { listOf(alpha, bravo, charlie) }

        assertEquals(10.0, alphaTarget.captured.x)
        assertEquals(0.0, alphaTarget.captured.z)
        assertEquals(0.0, bravoTarget.captured.x)
        assertEquals(0.0, bravoTarget.captured.z)
        assertEquals(10.0, charlieTarget.captured.x)
        assertEquals(0.0, charlieTarget.captured.z)
    }

    private fun mockPlugin(scheduler: BukkitScheduler): JavaPlugin {
        val server = mockk<Server>()
        val plugin = mockk<JavaPlugin>()
        every { server.scheduler } returns scheduler
        every { plugin.server } returns server
        return plugin
    }

    private fun mockConfigManager(
        showPlayerLocatorBar: Boolean,
        playerLocatorMaxAlivePlayers: Int = 4
    ): ConfigManager {
        val configManager = mockk<ConfigManager>()
        val gameSettings = mockk<GameSettings>()
        every { configManager.gameSettings } returns gameSettings
        every { gameSettings.showPlayerLocatorBar } returns showPlayerLocatorBar
        every { gameSettings.playerLocatorMaxAlivePlayers } returns playerLocatorMaxAlivePlayers
        return configManager
    }

    private fun mockPlayer(
        name: String,
        world: World,
        x: Double,
        z: Double,
        compassTargetSlot: io.mockk.CapturingSlot<Location>? = null
    ): Player {
        val location = Location(world, x, 64.0, z)
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.nameUUIDFromBytes(name.toByteArray())
        every { player.location } returns location
        if (compassTargetSlot != null) {
            every { player.compassTarget = capture(compassTargetSlot) } returns Unit
        } else {
            every { player.compassTarget = any() } returns Unit
        }
        return player
    }
}
