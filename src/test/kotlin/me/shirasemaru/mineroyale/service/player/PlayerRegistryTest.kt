package me.shirasemaru.mineroyale.service.player

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerRegistryTest {

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `markEliminated moves player from alive to spectator`() {
        val registry = PlayerRegistry()
        val player = mockPlayer("alpha")

        registry.resetForMatch(listOf(player))

        val eliminated = registry.markEliminated(player)

        assertTrue(eliminated)
        assertFalse(registry.isAlive(player))
        assertTrue(registry.isSpectator(player))
        assertEquals(0, registry.aliveCount())
    }

    @Test
    fun `getAlivePlayers and firstAlivePlayer only return online alive players`() {
        mockkStatic(Bukkit::class)

        val registry = PlayerRegistry()
        val aliveOnline = mockPlayer("alive-online", online = true)
        val aliveOffline = mockPlayer("alive-offline", online = false)
        val playersById = mapOf(
            aliveOnline.uniqueId to aliveOnline,
            aliveOffline.uniqueId to aliveOffline
        )

        registry.resetForMatch(listOf(aliveOnline, aliveOffline))

        every { Bukkit.getPlayer(any<UUID>()) } answers { playersById[firstArg()] }

        val alivePlayers = registry.getAlivePlayers()
        val firstAlive = registry.firstAlivePlayer()

        assertEquals(listOf(aliveOnline), alivePlayers)
        assertSame(aliveOnline, firstAlive)
    }

    @Test
    fun `addSpectator removes player from alive set`() {
        val registry = PlayerRegistry()
        val player = mockPlayer("spectator")

        registry.resetForMatch(listOf(player))
        registry.addSpectator(player)

        assertFalse(registry.isAlive(player))
        assertTrue(registry.isSpectator(player))
        assertEquals(0, registry.aliveCount())
    }

    private fun mockPlayer(name: String, online: Boolean = true): Player {
        val player = mockk<Player>()
        every { player.name } returns name
        every { player.uniqueId } returns UUID.nameUUIDFromBytes(name.toByteArray())
        every { player.isOnline } returns online
        return player
    }
}
