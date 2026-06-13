package me.shirasemaru.mineroyale.bootstrap

import io.mockk.every
import io.mockk.mockk
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.ConfiguredWorldNotFoundException
import me.shirasemaru.mineroyale.config.WorldSettings
import org.bukkit.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class GameWorldProviderTest {

    @Test
    fun `require returns configured world when it exists`() {
        val world = mockk<World>()
        val configManager = mockConfigManager(worldName = "world", world = world)
        val provider = BukkitGameWorldProvider(configManager)

        assertSame(world, provider.require())
    }

    @Test
    fun `require throws configured world exception when world is missing`() {
        val configManager = mockConfigManager(worldName = "missing_world", world = null)
        val provider = BukkitGameWorldProvider(configManager)

        val error = assertFailsWith<ConfiguredWorldNotFoundException> {
            provider.require()
        }

        assertEquals("missing_world", error.worldName)
    }

    private fun mockConfigManager(worldName: String, world: World?): ConfigManager {
        val configManager = mockk<ConfigManager>()
        every { configManager.worldSettings } returns WorldSettings(
            name = worldName,
            randomCenterRange = 1000.0,
            initialBorderSize = 1000.0
        )
        every { configManager.findGameWorld() } returns world
        return configManager
    }
}
