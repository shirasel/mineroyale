package me.shirasemaru.mineroyale12111.service.border

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.coroutines.waitTicks
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.math.abs

class BorderDamageService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val coroutineScope: CoroutineScope
) {

    private var damageJob: Job? = null
    private val outsideTime = mutableMapOf<UUID, Int>()
    private val trackedPlayers = linkedSetOf<UUID>()

    fun start(
        world: World,
        border: WorldBorder,
        aliveProvider: () -> List<Player>
    ) {
        stop()

        damageJob = coroutineScope.launch {
            plugin.waitTicks(40L)
            while (currentCoroutineContext().isActive) {
                tickDamage(border, aliveProvider)
                plugin.waitTicks(40L)
            }
        }
    }

    fun observePlayer(player: Player, border: WorldBorder, isAlive: Boolean) {
        val uuid = player.uniqueId
        if (!isAlive || !isOutsideBorder(player.location, border)) {
            trackedPlayers.remove(uuid)
            outsideTime.remove(uuid)
            return
        }

        trackedPlayers.add(uuid)
    }

    fun stop() {
        damageJob?.cancel()
        damageJob = null
        outsideTime.clear()
        trackedPlayers.clear()
    }

    private fun tickDamage(
        border: WorldBorder,
        aliveProvider: () -> List<Player>
    ) {
        val alivePlayers = aliveProvider()
        val aliveById = alivePlayers.associateBy(Player::getUniqueId)

        trackedPlayers.toList().forEach { uuid ->
            val player = aliveById[uuid]
            if (player == null || !isOutsideBorder(player.location, border)) {
                trackedPlayers.remove(uuid)
                outsideTime.remove(uuid)
                return@forEach
            }

            if (!configManager.borderSettings.enhancedDamage.enabled) {
                player.damage(1.0)
                return@forEach
            }

            val time = outsideTime.getOrDefault(uuid, 0) + 2
            outsideTime[uuid] = time

            val damage = minOf(
                configManager.borderSettings.enhancedDamage.baseDamage +
                    configManager.borderSettings.enhancedDamage.increasePerSecond * time,
                configManager.borderSettings.enhancedDamage.maxDamage
            )

            player.damage(damage)
        }
    }

    private fun isOutsideBorder(location: org.bukkit.Location, border: WorldBorder): Boolean {
        val center = border.center
        val radius = border.size / 2
        val damageRadius = radius + 1
        val dx = abs(location.x - center.x)
        val dz = abs(location.z - center.z)
        return dx > damageRadius || dz > damageRadius
    }
}
