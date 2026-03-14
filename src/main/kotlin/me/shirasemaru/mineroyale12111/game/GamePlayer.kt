package me.shirasemaru.mineroyale12111.game

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

class GamePlayer(val uuid: UUID) {

    val player: Player?
        get() = Bukkit.getPlayer(uuid)

    val isOnline: Boolean
        get() = player?.isOnline == true

    var isAlive: Boolean = true

    fun sendMessage(message: String) {
        player?.sendMessage(message)
    }

    fun teleport(location: Location) {
        player?.teleport(location)
    }

    fun playSound(sound: org.bukkit.Sound) {
        player?.playSound(player!!.location, sound, 1f, 1f)
    }
}