package me.shirasemaru.mineroyale12111.service.player

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class PlayerRegistry {

    private val alivePlayers = linkedSetOf<UUID>()
    private val spectators = linkedSetOf<UUID>()

    fun resetForMatch(players: Collection<Player>) {
        alivePlayers.clear()
        spectators.clear()
        players.forEach { alivePlayers.add(it.uniqueId) }
    }

    fun clear() {
        alivePlayers.clear()
        spectators.clear()
    }

    fun markEliminated(player: Player): Boolean {
        val removed = alivePlayers.remove(player.uniqueId)
        if (removed) {
            spectators.add(player.uniqueId)
        }
        return removed
    }

    fun addSpectator(player: Player) {
        alivePlayers.remove(player.uniqueId)
        spectators.add(player.uniqueId)
    }

    fun isAlive(player: Player): Boolean = alivePlayers.contains(player.uniqueId)

    fun isSpectator(player: Player): Boolean = spectators.contains(player.uniqueId)

    fun aliveCount(): Int = alivePlayers.size

    fun getAlivePlayers(): List<Player> =
        alivePlayers.mapNotNull { Bukkit.getPlayer(it) }.filter(Player::isOnline)

    fun getSpectators(): List<Player> =
        spectators.mapNotNull { Bukkit.getPlayer(it) }.filter(Player::isOnline)

    fun firstAlivePlayer(): Player? = getAlivePlayers().firstOrNull()
}
