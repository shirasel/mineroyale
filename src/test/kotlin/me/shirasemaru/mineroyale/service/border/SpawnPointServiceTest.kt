package me.shirasemaru.mineroyale.service.border

import io.mockk.every
import io.mockk.mockk
import me.shirasemaru.mineroyale.bootstrap.GameWorldProvider
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.SpawnSettings
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.block.Block
import org.bukkit.entity.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpawnPointServiceTest {

    @Test
    fun `generateRandomSpawnLocations returns a safe location inside the border`() {
        val world = mockWorld(
            highestYByColumn = mapOf(4 to mapOf(-3 to 70)),
            materialByColumn = mapOf(4 to mapOf(-3 to Material.GRASS_BLOCK))
        )
        val configManager = mockConfigManager(world, minDistance = 5.0)
        val service = SpawnPointService(configManager, mockWorldProvider(world), sequenceCoordinates(4.0, -3.0))
        val border = mockBorder(centerX = 0.0, centerZ = 0.0, size = 20.0)
        val player = mockPlayer("alpha")

        val result = service.generateRandomSpawnLocations(listOf(player), border)
        val spawn = result.getValue(player)

        assertSame(world, spawn.world)
        assertEquals(4.0, spawn.x, 0.001)
        assertEquals(71.0, spawn.y, 0.001)
        assertEquals(-3.0, spawn.z, 0.001)
        assertTrue(spawn.x in -9.0..9.0)
        assertTrue(spawn.z in -9.0..9.0)
    }

    @Test
    fun `generateRandomSpawnLocations skips unsafe and too-close candidates`() {
        val world = mockWorld(
            highestYByColumn = mapOf(
                2 to mapOf(2 to 64),
                4 to mapOf(4 to 65),
                5 to mapOf(5 to 66),
                8 to mapOf(8 to 67)
            ),
            materialByColumn = mapOf(
                2 to mapOf(2 to Material.LAVA),
                4 to mapOf(4 to Material.GRASS_BLOCK),
                5 to mapOf(5 to Material.GRASS_BLOCK),
                8 to mapOf(8 to Material.GRASS_BLOCK)
            )
        )
        val configManager = mockConfigManager(world, minDistance = 5.0)
        val service = SpawnPointService(
            configManager,
            mockWorldProvider(world),
            sequenceCoordinates(
                2.0, 2.0,
                4.0, 4.0,
                5.0, 5.0,
                8.0, 8.0
            )
        )
        val border = mockBorder(centerX = 0.0, centerZ = 0.0, size = 20.0)
        val playerA = mockPlayer("alpha")
        val playerB = mockPlayer("bravo")

        val result = service.generateRandomSpawnLocations(listOf(playerA, playerB), border)

        val spawnA = result.getValue(playerA)
        val spawnB = result.getValue(playerB)

        assertEquals(4.0, spawnA.x, 0.001)
        assertEquals(66.0, spawnA.y, 0.001)
        assertEquals(4.0, spawnA.z, 0.001)

        assertEquals(8.0, spawnB.x, 0.001)
        assertEquals(68.0, spawnB.y, 0.001)
        assertEquals(8.0, spawnB.z, 0.001)
        assertTrue(spawnA.distanceSquared(spawnB) >= 25.0)
    }

    @Test
    fun `generateRandomSpawnLocations falls back to border center after repeated failures`() {
        val world = mockWorld(
            highestYByColumn = mapOf(
                1 to mapOf(1 to 70),
                0 to mapOf(0 to 64)
            ),
            materialByColumn = mapOf(
                1 to mapOf(1 to Material.LAVA),
                0 to mapOf(0 to Material.GRASS_BLOCK)
            )
        )
        val configManager = mockConfigManager(world, minDistance = 5.0)
        val repeatedUnsafe = DoubleArray(100) { 1.0 }
        val service = SpawnPointService(configManager, mockWorldProvider(world), sequenceCoordinates(*repeatedUnsafe))
        val border = mockBorder(centerX = 0.0, centerZ = 0.0, size = 20.0)
        val player = mockPlayer("fallback")

        val result = service.generateRandomSpawnLocations(listOf(player), border)
        val spawn = result.getValue(player)

        assertEquals(0.0, spawn.x, 0.001)
        assertEquals(66.0, spawn.y, 0.001)
        assertEquals(0.0, spawn.z, 0.001)
    }

    private fun mockConfigManager(world: World, minDistance: Double): ConfigManager {
        val configManager = mockk<ConfigManager>()
        every { configManager.gameWorld } returns world
        every { configManager.spawnSettings } returns SpawnSettings(minDistance = minDistance)
        return configManager
    }

    private fun mockWorldProvider(world: World): GameWorldProvider =
        mockk {
            every { require() } returns world
            every { find() } returns world
        }

    private fun mockWorld(
        highestYByColumn: Map<Int, Map<Int, Int>>,
        materialByColumn: Map<Int, Map<Int, Material>>
    ): World {
        val world = mockk<World>()
        every { world.getHighestBlockYAt(any<Int>(), any<Int>()) } answers {
            highestYByColumn[firstArg<Int>()]?.get(secondArg<Int>())
                ?: error("Missing highest Y for (${firstArg<Int>()}, ${secondArg<Int>()})")
        }
        every { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
            val x = firstArg<Int>()
            val z = thirdArg<Int>()
            val block = mockk<Block>()
            every { block.type } returns (
                materialByColumn[x]?.get(z)
                    ?: error("Missing material for ($x, $z)")
                )
            block
        }
        return world
    }

    private fun mockBorder(centerX: Double, centerZ: Double, size: Double): WorldBorder {
        val border = mockk<WorldBorder>()
        every { border.center } returns Location(null, centerX, 0.0, centerZ)
        every { border.size } returns size
        return border
    }

    private fun mockPlayer(name: String): Player {
        val player = mockk<Player>()
        every { player.name } returns name
        return player
    }

    private fun sequenceCoordinates(vararg values: Double): (Double, Double) -> Double {
        var index = 0
        return { _, _ ->
            values.getOrNull(index++) ?: error("No more test coordinates available")
        }
    }
}
