package me.shirasemaru.mineroyale.service.player

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpectatorServiceTest {

    @Test
    fun `isNavigatorRod rejects ordinary blaze rod without plugin marker`() {
        val service = SpectatorService(mockPlugin())
        val item = mockNavigatorCandidate(hasMarker = false)

        assertFalse(service.isNavigatorRod(item))
    }

    @Test
    fun `isNavigatorRod accepts blaze rod with plugin marker`() {
        val service = SpectatorService(mockPlugin())
        val item = mockNavigatorCandidate(hasMarker = true)

        assertTrue(service.isNavigatorRod(item))
    }

    @Test
    fun `isNavigatorRod rejects non blaze rod even with plugin marker`() {
        val service = SpectatorService(mockPlugin())
        val item = mockNavigatorCandidate(
            material = Material.STICK,
            hasMarker = true
        )

        assertFalse(service.isNavigatorRod(item))
    }

    private fun mockPlugin(): JavaPlugin {
        val plugin = mockk<JavaPlugin>()
        every { plugin.name } returns "mineroyale"
        every { plugin.namespace() } returns "mineroyale"
        return plugin
    }

    private fun mockNavigatorCandidate(
        material: Material = Material.BLAZE_ROD,
        hasMarker: Boolean
    ): ItemStack {
        val item = mockk<ItemStack>()
        val meta = mockk<ItemMeta>()
        val container = mockk<PersistentDataContainer>()

        every { item.type } returns material
        every { item.itemMeta } returns meta
        every { meta.persistentDataContainer } returns container
        every {
            container.has(any<NamespacedKey>(), PersistentDataType.BYTE)
        } returns hasMarker

        return item
    }
}
