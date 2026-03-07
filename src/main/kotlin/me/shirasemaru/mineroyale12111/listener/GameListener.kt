package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

class GameListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {

        if (!gameManager.isRunning()) return

        gameManager.handlePlayerDeath(event.entity)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {

        if (!gameManager.isRunning()) return

        gameManager.handlePlayerDeath(event.player)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {

        if (!gameManager.isRunning()) return

        gameManager.handlePlayerDeath(event.player)
    }

    /*
     * PvP制御
     */
    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {

        if (!gameManager.isRunning()) return

        val victim = event.entity
        val attacker = event.damager

        if (victim !is Player) return
        if (attacker !is Player) return

        if (!gameManager.isPvpEnabled()) {
            event.isCancelled = true
        }
    }
}