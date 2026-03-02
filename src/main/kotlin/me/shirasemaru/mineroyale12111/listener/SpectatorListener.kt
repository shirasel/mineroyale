package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.meta.SkullMeta

class SpectatorListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        val player = event.player

        if (player.gameMode != GameMode.SPECTATOR) return

        val item = event.item ?: return
        if (item.type != Material.PLAYER_HEAD) return

        val meta = item.itemMeta as? SkullMeta ?: return
        val target = meta.owningPlayer ?: return

        val targetPlayer = Bukkit.getPlayer(target.uniqueId) ?: return
        if (!targetPlayer.isOnline) return

        player.teleport(targetPlayer.location)
    }
}