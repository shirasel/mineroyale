@file:Suppress("DEPRECATION")

package me.shirasemaru.mineroyale12111.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpectatorListenerTest {

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `onRightClick teleports spectator to alive target and cancels event`() {
        mockkStatic(Bukkit::class)

        val gameManager = mockk<GameManager>()
        val spectator = mockk<Player>()
        val target = mockk<Player>()
        val item = mockPlayerHead()
        val event = mockInteractEvent(
            player = spectator,
            action = Action.RIGHT_CLICK_AIR,
            item = item
        )
        val listener = SpectatorListener(gameManager)

        every { gameManager.isRunning() } returns true
        every { spectator.gameMode } returns GameMode.SPECTATOR
        every { gameManager.extractSpectatorTargetName(item) } returns "target"
        every { Bukkit.getPlayerExact("target") } returns target
        every { target.isOnline } returns true
        every { gameManager.isAlive(target) } returns true
        every { gameManager.teleportSpectator(spectator, target) } answers { event.setCancelled(true) }

        listener.onRightClick(event)

        assertTrue(event.isCancelled())
        verify(exactly = 1) { gameManager.teleportSpectator(spectator, target) }
    }

    @Test
    fun `onRightClick ignores interaction when player is not spectator`() {
        val gameManager = mockk<GameManager>()
        val player = mockk<Player>()
        val item = mockPlayerHead()
        val event = mockInteractEvent(
            player = player,
            action = Action.RIGHT_CLICK_AIR,
            item = item
        )
        val listener = SpectatorListener(gameManager)

        every { gameManager.isRunning() } returns true
        every { player.gameMode } returns GameMode.SURVIVAL

        listener.onRightClick(event)

        assertFalse(event.isCancelled())
        verify(exactly = 0) { gameManager.extractSpectatorTargetName(any()) }
        verify(exactly = 0) { gameManager.teleportSpectator(any(), any()) }
    }

    @Test
    fun `onRightClick ignores interaction when target is offline or not alive`() {
        mockkStatic(Bukkit::class)

        val gameManager = mockk<GameManager>()
        val spectator = mockk<Player>()
        val target = mockk<Player>()
        val item = mockPlayerHead()
        val event = mockInteractEvent(
            player = spectator,
            action = Action.RIGHT_CLICK_BLOCK,
            item = item
        )
        val listener = SpectatorListener(gameManager)

        every { gameManager.isRunning() } returns true
        every { spectator.gameMode } returns GameMode.SPECTATOR
        every { gameManager.extractSpectatorTargetName(item) } returns "target"
        every { Bukkit.getPlayerExact("target") } returns target
        every { target.isOnline } returns false

        listener.onRightClick(event)

        assertFalse(event.isCancelled())
        verify(exactly = 0) { gameManager.isAlive(any()) }
        verify(exactly = 0) { gameManager.teleportSpectator(any(), any()) }
    }

    private fun mockInteractEvent(
        player: Player,
        action: Action,
        item: ItemStack?
    ): PlayerInteractEvent {
        var cancelled = false
        val event = mockk<PlayerInteractEvent>()
        every { event.player } returns player
        every { event.action } returns action
        every { event.item } returns item
        every { event.isCancelled() } answers { cancelled }
        every { event.setCancelled(any()) } answers { cancelled = firstArg() }
        return event
    }

    private fun mockPlayerHead(): ItemStack {
        val item = mockk<ItemStack>()
        every { item.type } returns Material.PLAYER_HEAD
        return item
    }
}
