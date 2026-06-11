package me.shirasemaru.mineroyale.bootstrap

import me.shirasemaru.mineroyale.config.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.World

interface GameWorldProvider {
    fun find(): World?
    fun require(): World
}

class BukkitGameWorldProvider(
    private val configManager: ConfigManager
) : GameWorldProvider {

    override fun find(): World? =
        Bukkit.getWorld(configManager.worldSettings.name)

    override fun require(): World =
        find() ?: error("World '${configManager.worldSettings.name}' not found")
}
