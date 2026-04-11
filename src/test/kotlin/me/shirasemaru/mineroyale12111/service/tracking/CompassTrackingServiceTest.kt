package me.shirasemaru.mineroyale12111.service.tracking

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.config.GameSettings
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import kotlin.test.Test

class CompassTrackingServiceTest {

    @Test
    fun `start does not schedule locator updates when disabled in config`() {
        val scheduler = mockk<BukkitScheduler>(relaxed = true)
        val service = CompassTrackingService(
            plugin = mockPlugin(scheduler),
            configManager = mockConfigManager(showPlayerLocatorBar = false)
        )

        service.start { emptyList() }

        verify(exactly = 0) { scheduler.runTaskTimer(any<JavaPlugin>(), any<Runnable>(), any<Long>(), any<Long>()) }
    }

    @Test
    fun `start schedules locator updates when enabled in config`() {
        val scheduler = mockk<BukkitScheduler>()
        every { scheduler.runTaskTimer(any<JavaPlugin>(), any<Runnable>(), any<Long>(), any<Long>()) } returns mockk<BukkitTask>(relaxed = true)

        val service = CompassTrackingService(
            plugin = mockPlugin(scheduler),
            configManager = mockConfigManager(showPlayerLocatorBar = true)
        )

        service.start { emptyList() }

        verify(exactly = 1) { scheduler.runTaskTimer(any<JavaPlugin>(), any<Runnable>(), 0L, 40L) }
    }

    private fun mockPlugin(scheduler: BukkitScheduler): JavaPlugin {
        val server = mockk<Server>()
        val plugin = mockk<JavaPlugin>()
        every { server.scheduler } returns scheduler
        every { plugin.server } returns server
        return plugin
    }

    private fun mockConfigManager(showPlayerLocatorBar: Boolean): ConfigManager {
        val configManager = mockk<ConfigManager>()
        val gameSettings = mockk<GameSettings>()
        every { configManager.gameSettings } returns gameSettings
        every { gameSettings.showPlayerLocatorBar } returns showPlayerLocatorBar
        return configManager
    }
}
