package me.shirasemaru.mineroyale.service.game

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeathMarkerServiceTest {

    @Test
    fun `isMarker recognizes persisted death marker tag`() {
        val service = DeathMarkerService(mockPlugin(emptyList()))
        val marker = mockEntity(hasMarkerTag = true)
        val ordinaryEntity = mockEntity(hasMarkerTag = false)

        assertTrue(service.isMarker(marker))
        assertFalse(service.isMarker(ordinaryEntity))
    }

    @Test
    fun `clearMarkers removes persisted death markers from worlds`() {
        val marker = mockEntity(hasMarkerTag = true)
        val ordinaryEntity = mockEntity(hasMarkerTag = false)
        val world = mockk<World>()
        val service = DeathMarkerService(mockPlugin(listOf(world)))

        every { world.entities } returns listOf(marker, ordinaryEntity)

        service.clearMarkers()

        verify(exactly = 1) { marker.remove() }
        verify(exactly = 0) { ordinaryEntity.remove() }
    }

    private fun mockPlugin(worlds: List<World>): JavaPlugin {
        val server = mockk<Server>()
        val plugin = mockk<JavaPlugin>()
        every { plugin.name } returns "mineroyale"
        every { plugin.namespace() } returns "mineroyale"
        every { plugin.server } returns server
        every { server.worlds } returns worlds
        every { server.getEntity(any()) } returns null
        return plugin
    }

    private fun mockEntity(hasMarkerTag: Boolean): Entity {
        val entity = mockk<Entity>()
        val container = mockk<PersistentDataContainer>()
        every { entity.uniqueId } returns java.util.UUID.randomUUID()
        every { entity.persistentDataContainer } returns container
        every {
            container.has(any<NamespacedKey>(), PersistentDataType.BYTE)
        } returns hasMarkerTag
        every { entity.remove() } returns Unit
        return entity
    }
}
