package me.shirasemaru.mineroyale12111.service.game

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

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

    fun playVictory(winner: Player, onFinished: () -> Unit) {
        messageService.broadcastVictory(winner.name)

        val title = messageService.victoryTitle(winner.name)
        Bukkit.getOnlinePlayers().forEach { it.showTitle(title) }

        val location = winner.location.clone().add(0.0, 1.0, 0.0)
        teleportPlayersInBatches(Bukkit.getOnlinePlayers().toList(), location) {
            scheduleFireworks(winner)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { onFinished() }, RESET_DELAY_AFTER_EFFECTS_TICKS)
        }
    }

    private fun teleportPlayersInBatches(players: List<Player>, location: Location, onComplete: () -> Unit) {
        if (players.isEmpty()) {
            onComplete()
            return
        }

        var index = 0
        var task: BukkitTask? = null
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            players.drop(index).take(TELEPORT_BATCH_SIZE).forEach { player ->
                player.teleport(location)
            }

            index += TELEPORT_BATCH_SIZE
            if (index >= players.size) {
                task?.cancel()
                onComplete()
            }
        }, 0L, TELEPORT_BATCH_INTERVAL_TICKS)
    }

    private fun scheduleFireworks(winner: Player) {
        repeat(FIREWORK_COUNT) { index ->
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable { spawnFirework(winner) },
                index * FIREWORK_INTERVAL_TICKS
            )
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
