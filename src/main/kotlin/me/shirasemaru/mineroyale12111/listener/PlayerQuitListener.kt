package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.game.GameState
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class PlayerQuitListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {

        if (gameManager.state != GameState.RUNNING) return

        gameManager.handleDeath(event.player)
    }
}