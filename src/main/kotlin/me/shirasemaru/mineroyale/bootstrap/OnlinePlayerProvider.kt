package me.shirasemaru.mineroyale.bootstrap

import org.bukkit.Bukkit
import org.bukkit.entity.Player

interface OnlinePlayerProvider {
    val onlinePlayers: Collection<Player>
}

class BukkitOnlinePlayerProvider : OnlinePlayerProvider {
    override val onlinePlayers: Collection<Player>
        get() = Bukkit.getOnlinePlayers()
}
