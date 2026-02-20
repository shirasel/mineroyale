package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class PlayerDeathListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        gameManager.handleDeath(event.entity)
    }
}
