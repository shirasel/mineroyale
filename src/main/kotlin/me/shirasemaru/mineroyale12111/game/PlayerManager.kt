package me.shirasemaru.mineroyale12111.game

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class PlayerManager {

    private val players = mutableMapOf<UUID, GamePlayer>()

    fun registerAllOnline(playersList: List<Player>) {
        playersList.forEach {
            players[it.uniqueId] = GamePlayer(it.uniqueId)
        }
    }

    fun getAlivePlayers(): List<GamePlayer> =
        players.values.filter {
            it.isAlive && it.player?.isOnline == true
        }

    fun markDead(uuid: UUID): Boolean {

        val gp = players[uuid] ?: return false

        if (!gp.isAlive) return false

        gp.isAlive = false
        return true
    }

    fun isAlive(uuid: UUID) =
        players[uuid]?.isAlive == true

    fun clear() {
        players.clear()
    }
}