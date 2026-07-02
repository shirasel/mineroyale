package me.shirasemaru.mineroyale.bootstrap

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

interface OfflinePlayerResolver {
    fun resolve(name: String): OfflinePlayer
}

class BukkitOfflinePlayerResolver : OfflinePlayerResolver {
    override fun resolve(name: String): OfflinePlayer =
        Bukkit.getOfflinePlayer(name)
}
