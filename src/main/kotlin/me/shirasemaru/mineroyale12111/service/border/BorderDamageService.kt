package me.shirasemaru.mineroyale12111.service.border

import me.shirasemaru.mineroyale12111.config.ConfigManager
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.abs

class BorderDamageService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {

    private var damageTask: BukkitTask? = null
    private val outsideTime = mutableMapOf<UUID, Int>()

    fun start(world: World, border: WorldBorder) {
        stop()

        damageTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val center = border.center
            val radius = border.size / 2
            val damageRadius = radius + 1

            world.players.forEach { player ->
                val dx = abs(player.location.x - center.x)
                val dz = abs(player.location.z - center.z)
                val outside = dx > damageRadius || dz > damageRadius
                val uuid = player.uniqueId

                if (!outside) {
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
        }, 40L, 40L)
    }

    fun stop() {
        damageTask?.cancel()
        damageTask = null
        outsideTime.clear()
    }
}
