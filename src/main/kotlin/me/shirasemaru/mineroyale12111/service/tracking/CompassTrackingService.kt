package me.shirasemaru.mineroyale12111.service.tracking

import me.shirasemaru.mineroyale12111.config.ConfigManager
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class CompassTrackingService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {

    private var task: BukkitTask? = null

    fun start(aliveProvider: () -> List<Player>) {
        stop()

        if (!configManager.gameSettings.showPlayerLocatorBar) {
            return
        }

        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val alivePlayers = aliveProvider()
            val maxAlivePlayers = configManager.gameSettings.playerLocatorMaxAlivePlayers

            if (alivePlayers.size > maxAlivePlayers) return@Runnable
            if (alivePlayers.size <= 1) return@Runnable

            alivePlayers.forEach { player ->
                val nearest = alivePlayers
                    .asSequence()
                    .filter { it.uniqueId != player.uniqueId }
                    .minByOrNull { it.location.distanceSquared(player.location) }

                if (nearest != null) {
                    player.compassTarget = nearest.location
                }
            }
        }, 0L, 40L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }
}
