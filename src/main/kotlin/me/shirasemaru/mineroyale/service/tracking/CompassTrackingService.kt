package me.shirasemaru.mineroyale.service.tracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.coroutines.waitTicks
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class CompassTrackingService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val coroutineScope: CoroutineScope
) {

    private var trackingJob: Job? = null

    fun start(aliveProvider: () -> List<Player>) {
        stop()

        if (!configManager.gameSettings.showPlayerLocatorBar) {
            return
        }

        trackingJob = coroutineScope.launch {
            while (currentCoroutineContext().isActive) {
                updateCompassTargets(aliveProvider)
                plugin.waitTicks(40L)
            }
        }
    }

    fun stop() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun updateCompassTargets(aliveProvider: () -> List<Player>) {
        val alivePlayers = aliveProvider()
        val maxAlivePlayers = configManager.gameSettings.playerLocatorMaxAlivePlayers

        if (alivePlayers.size > maxAlivePlayers) return
        if (alivePlayers.size <= 1) return

        alivePlayers.forEach { player ->
            val playerLocation = player.location
            val nearest = alivePlayers
                .asSequence()
                .filter { it.uniqueId != player.uniqueId }
                .minByOrNull { other -> other.location.distanceSquared(playerLocation) }

            if (nearest != null) {
                player.compassTarget = nearest.location
            }
        }
    }
}
