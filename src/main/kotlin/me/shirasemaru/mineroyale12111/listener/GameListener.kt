package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

class GameListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        gameManager.handleDeath(player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        gameManager.forceEliminate(event.player)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        gameManager.forceEliminate(event.player)
    }
}