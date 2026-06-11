package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent

class SpectatorListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        val player = event.player

        if (!gameManager.isRunning()) return
        if (!gameManager.isSpectator(player)) return
        if (event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.RIGHT_CLICK_BLOCK
        ) return

        val item = event.item ?: return
        if (!gameManager.isSpectatorNavigator(item)) return

        event.isCancelled = true
        gameManager.openSpectatorMenu(player)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return

        if (!gameManager.isRunning()) return
        if (!gameManager.isSpectator(player)) return
        if (!gameManager.isSpectatorMenu(event.view.title())) return

        event.isCancelled = true

        val item = event.currentItem ?: return
        if (item.type != Material.PLAYER_HEAD) return

        val targetName = gameManager.extractSpectatorTargetName(item) ?: return
        val targetPlayer = Bukkit.getPlayerExact(targetName) ?: return
        if (!targetPlayer.isOnline) return
        if (!gameManager.isAlive(targetPlayer)) return

        event.isCancelled = true
        gameManager.teleportSpectator(player, targetPlayer)
    }
}
