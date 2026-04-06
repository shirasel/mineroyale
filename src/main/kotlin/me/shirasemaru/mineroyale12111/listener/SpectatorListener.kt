package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class SpectatorListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        val player = event.player

        if (!gameManager.isRunning()) return
        if (player.gameMode != GameMode.SPECTATOR) return
        if (event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.RIGHT_CLICK_BLOCK
        ) return

        val item = event.item ?: return
        if (item.type != Material.PLAYER_HEAD) return

        val targetName = gameManager.extractSpectatorTargetName(item) ?: return
        val targetPlayer = Bukkit.getPlayerExact(targetName) ?: return
        if (!targetPlayer.isOnline) return
        if (!gameManager.isAlive(targetPlayer)) return

        event.isCancelled = true
        gameManager.teleportSpectator(player, targetPlayer)
    }
}
