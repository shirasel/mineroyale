package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class GameListener(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
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
            Runnable { gameManager.handlePlayerDeath(player, deathLocations[player.uniqueId]) },
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
        val victim = event.entity
        if (gameManager.isProtectedDeathMarker(victim)) {
            event.isCancelled = true
            return
        }

        if (!gameManager.isRunning()) return

        if (victim !is Player) return

        val attacker: Player? = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        }

        if (attacker == null) return

        if (gameManager.isSpectator(victim) || gameManager.isSpectator(attacker)) {
            event.isCancelled = true
            return
        }

        if (!gameManager.isPvpEnabled()) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        if (!gameManager.isProtectedDeathMarker(event.rightClicked)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!shouldRestrictBlockModification(event.block.location)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!shouldRestrictBlockModification(event.block.location)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        if (!gameManager.isRunning()) return

        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) {
            return
        }

        gameManager.observeBorderDamageTarget(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val respawnOverride = gameManager.respawnOverrideLocation()
        if (!gameManager.isRunning() && respawnOverride == null) return

        val player = event.player
        val location = deathLocations.remove(player.uniqueId) ?: respawnOverride

        if (location != null) {
            event.respawnLocation = location
        }

        if (gameManager.isRunning() && gameManager.isSpectator(player)) {
            player.gameMode = GameMode.SPECTATOR
            gameManager.reapplySpectatorMode(player)
        }
    }

    private fun shouldRestrictBlockModification(location: Location): Boolean {
        if (!gameManager.isRunning()) return false
        if (!configManager.gameSettings.restrictBlockModificationOutsideBorder) return false
        return gameManager.isOutsideCurrentBorder(location)
    }
}
