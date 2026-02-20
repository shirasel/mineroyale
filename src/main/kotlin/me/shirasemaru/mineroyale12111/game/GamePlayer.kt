package me.shirasemaru.mineroyale12111.game

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class GamePlayer(val uuid: UUID) {

    val player: Player?
        get() = Bukkit.getPlayer(uuid)

    var isAlive: Boolean = true

    fun sendMessage(message: String) {
        player?.sendMessage(message)
    }

    fun teleport(location: org.bukkit.Location) {
        player?.teleport(location)
    }
}
