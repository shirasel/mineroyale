package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class GameListener(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager
) : Listener {

    private val deathLocations = HashMap<UUID, Location>()

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (!gameManager.isRunning()) return

        val player = event.entity
        deathLocations[player.uniqueId] = player.location.clone()

        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable { gameManager.handlePlayerDeath(player) },
            1L
        )
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

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (!gameManager.isRunning()) return

        val victim = event.entity
        if (victim !is Player) return

        val attacker: Player? = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        }

        if (attacker == null) return

        if (!gameManager.isPvpEnabled()) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        if (!gameManager.isRunning()) return

        val player = event.player
        val location = deathLocations.remove(player.uniqueId)

        if (location != null) {
            event.respawnLocation = location
        }

        if (gameManager.isSpectator(player)) {
            player.gameMode = GameMode.SPECTATOR
        }
    }
}
