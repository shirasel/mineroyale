package me.shirasemaru.mineroyale12111.service.game

import me.shirasemaru.mineroyale12111.coroutines.waitTicks
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class VictoryService(
    private val plugin: JavaPlugin,
    private val messageService: MessageService
) {

    private companion object {
        const val TELEPORT_BATCH_SIZE = 4
        const val TELEPORT_BATCH_INTERVAL_TICKS = 2L
        const val FIREWORK_COUNT = 3
        const val FIREWORK_INTERVAL_TICKS = 10L
        const val RESET_DELAY_AFTER_EFFECTS_TICKS = 60L
    }

    suspend fun playVictory(winner: Player) {
        messageService.broadcastVictory(winner.name)

        val title = messageService.victoryTitle(winner.name)
        Bukkit.getOnlinePlayers().forEach { it.showTitle(title) }

        val location = winner.location.clone().add(0.0, 1.0, 0.0)
        teleportPlayersInBatches(Bukkit.getOnlinePlayers().toList(), location)
        scheduleFireworks(winner)
        plugin.waitTicks(RESET_DELAY_AFTER_EFFECTS_TICKS)
    }

    private suspend fun teleportPlayersInBatches(players: List<Player>, location: Location) {
        if (players.isEmpty()) {
            return
        }

        var index = 0
        while (index < players.size) {
            players.drop(index).take(TELEPORT_BATCH_SIZE).forEach { player ->
                player.teleport(location)
            }
            index += TELEPORT_BATCH_SIZE
            if (index < players.size) {
                plugin.waitTicks(TELEPORT_BATCH_INTERVAL_TICKS)
            }
        }
    }

    private suspend fun scheduleFireworks(winner: Player) {
        repeat(FIREWORK_COUNT) { index ->
            if (index > 0) {
                plugin.waitTicks(FIREWORK_INTERVAL_TICKS)
            }
            spawnFirework(winner)
        }
    }

    private fun spawnFirework(winner: Player) {
        val firework = winner.world.spawn(winner.location, Firework::class.java)
        val meta = firework.fireworkMeta

        meta.addEffect(
            FireworkEffect.builder()
                .withColor(Color.ORANGE)
                .withFade(Color.YELLOW)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build()
        )

        meta.power = 1
        firework.fireworkMeta = meta
    }
}
