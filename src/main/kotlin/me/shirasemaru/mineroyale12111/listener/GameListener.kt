package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.game.GameState
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

        if (gameManager.state != GameState.RUNNING) return

        gameManager.handleDeath(event.entity)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {

        if (gameManager.state != GameState.RUNNING) return

        gameManager.forceEliminate(event.player)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {

        if (gameManager.state != GameState.RUNNING) return

        gameManager.forceEliminate(event.player)
    }
}