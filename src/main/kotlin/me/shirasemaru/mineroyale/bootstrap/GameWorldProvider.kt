package me.shirasemaru.mineroyale.bootstrap

import me.shirasemaru.mineroyale.config.ConfiguredWorldNotFoundException
import me.shirasemaru.mineroyale.config.ConfigManager
import org.bukkit.World

interface GameWorldProvider {
    fun find(): World?
    fun require(): World
}

class BukkitGameWorldProvider(
    private val configManager: ConfigManager
) : GameWorldProvider {

    override fun find(): World? =
        configManager.findGameWorld()

    override fun require(): World =
        find() ?: throw ConfiguredWorldNotFoundException(configManager.worldSettings.name)
}
