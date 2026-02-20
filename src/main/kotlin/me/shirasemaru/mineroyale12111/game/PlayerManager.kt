package me.shirasemaru.mineroyale12111.game

import org.bukkit.Bukkit
import java.util.UUID

class PlayerManager {

    private val players = mutableMapOf<UUID, GamePlayer>()

    fun registerAllOnline() {
        Bukkit.getOnlinePlayers().forEach {
            players[it.uniqueId] = GamePlayer(it.uniqueId)
        }
    }

    fun getAlivePlayers(): List<GamePlayer> =
        players.values.filter { it.isAlive }

    fun markDead(uuid: UUID) {
        players[uuid]?.isAlive = false
    }

    fun isAlive(uuid: UUID): Boolean =
        players[uuid]?.isAlive == true

    fun clear() {
        players.clear()
    }
}