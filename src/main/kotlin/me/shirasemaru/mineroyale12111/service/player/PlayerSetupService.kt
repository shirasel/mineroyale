package me.shirasemaru.mineroyale12111.service.player

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player

class PlayerSetupService {

    fun preparePlayerForLobby(player: Player) {
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        resetPlayerState(player)
        player.gameMode = GameMode.SURVIVAL
    }

    fun prepareLateJoinSpectator(player: Player) {
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        resetPlayerState(player)
        player.gameMode = GameMode.SPECTATOR
    }

    fun prepareMatchPlayers(spawnMap: Map<Player, Location>) {
        spawnMap.forEach { (player, location) ->
            resetPlayerState(player)
            player.gameMode = GameMode.SURVIVAL
            player.teleport(location)
        }
    }

    fun resetAllOnlinePlayersToLobby() {
        Bukkit.getOnlinePlayers().forEach { player ->
            resetPlayerState(player)
            player.gameMode = GameMode.ADVENTURE
        }
    }

    private fun resetPlayerState(player: Player) {
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.fallDistance = 0f
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.inventory.clear()
        player.inventory.armorContents = arrayOf(null, null, null, null)
        player.inventory.setItemInOffHand(null)
    }
}
