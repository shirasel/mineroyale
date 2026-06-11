package me.shirasemaru.mineroyale.listener

import me.shirasemaru.mineroyale.game.GameManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        if (gameManager.isRunning()) {
            gameManager.handleLateJoin(player)
        } else {
            gameManager.prepareLobbyPlayer(player)
        }
    }
}
