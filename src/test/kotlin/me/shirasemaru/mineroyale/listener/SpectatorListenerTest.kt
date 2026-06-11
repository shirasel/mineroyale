package me.shirasemaru.mineroyale.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import me.shirasemaru.mineroyale.game.GameManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.InventoryView
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
    fun `onRightClick opens spectator menu when navigator rod is used`() {
        val gameManager = mockk<GameManager>()
        val spectator = mockk<Player>()
        val item = mockItem(Material.BLAZE_ROD)
        val event = mockInteractEvent(
            player = spectator,
            action = Action.RIGHT_CLICK_AIR,
            item = item
        )
        val listener = SpectatorListener(gameManager)

        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(spectator) } returns true
        every { gameManager.isSpectatorNavigator(item) } returns true
        every { gameManager.openSpectatorMenu(spectator) } returns Unit

        listener.onRightClick(event)

        verify(exactly = 1) { event.setCancelled(true) }
        verify(exactly = 1) { gameManager.openSpectatorMenu(spectator) }
    }

    @Test
    fun `onRightClick ignores interaction when player is not spectator`() {
        val gameManager = mockk<GameManager>()
        val player = mockk<Player>()
        val item = mockItem(Material.BLAZE_ROD)
        val event = mockInteractEvent(
            player = player,
            action = Action.RIGHT_CLICK_AIR,
            item = item
        )
        val listener = SpectatorListener(gameManager)

        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(player) } returns false

        listener.onRightClick(event)

        verify(exactly = 0) { event.setCancelled(any()) }
        verify(exactly = 0) { gameManager.openSpectatorMenu(any()) }
    }

    @Test
    fun `onInventoryClick teleports spectator to alive target and cancels event`() {
        mockkStatic(Bukkit::class)

        val gameManager = mockk<GameManager>()
        val spectator = mockk<Player>()
        val target = mockk<Player>()
        val item = mockItem(Material.PLAYER_HEAD)
        val event = mockInventoryClickEvent(
            player = spectator,
            click = ClickType.LEFT,
            item = item,
            title = "観戦先選択"
        )
        val listener = SpectatorListener(gameManager)

        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(spectator) } returns true
        every { gameManager.isSpectatorMenu(Component.text("観戦先選択")) } returns true
        every { gameManager.extractSpectatorTargetName(item) } returns "target"
        every { Bukkit.getPlayerExact("target") } returns target
        every { target.isOnline } returns true
        every { gameManager.isAlive(target) } returns true
        every { gameManager.teleportSpectator(spectator, target) } returns Unit

        listener.onInventoryClick(event)

        assertTrue(event.isCancelled)
        verify(exactly = 1) { gameManager.teleportSpectator(spectator, target) }
    }

    @Test
    fun `onInventoryClick ignores clicks outside spectator menu`() {
        val gameManager = mockk<GameManager>()
        val spectator = mockk<Player>()
        val item = mockItem(Material.PLAYER_HEAD)
        val event = mockInventoryClickEvent(
            player = spectator,
            click = ClickType.LEFT,
            item = item,
            title = "他の画面"
        )
        val listener = SpectatorListener(gameManager)

        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(spectator) } returns true
        every { gameManager.isSpectatorMenu(Component.text("他の画面")) } returns false

        listener.onInventoryClick(event)

        assertFalse(event.isCancelled)
        verify(exactly = 0) { gameManager.teleportSpectator(any(), any()) }
    }

    private fun mockInteractEvent(
        player: Player,
        action: Action,
        item: ItemStack?
    ): PlayerInteractEvent {
        val event = mockk<PlayerInteractEvent>()
        every { event.player } returns player
        every { event.action } returns action
        every { event.item } returns item
        every { event.setCancelled(any()) } returns Unit
        return event
    }

    private fun mockInventoryClickEvent(
        player: Player,
        click: ClickType,
        item: ItemStack?,
        title: String
    ): InventoryClickEvent {
        var cancelled = false
        val view = mockk<InventoryView>()
        val event = mockk<InventoryClickEvent>()
        every { event.whoClicked } returns player
        every { event.click } returns click
        every { event.currentItem } returns item
        every { event.view } returns view
        every { view.title() } returns Component.text(title)
        every { event.isCancelled } answers { cancelled }
        every { event.isCancelled = any() } answers { cancelled = firstArg() }
        return event
    }

    private fun mockItem(material: Material): ItemStack {
        val item = mockk<ItemStack>()
        every { item.type } returns material
        return item
    }
}
