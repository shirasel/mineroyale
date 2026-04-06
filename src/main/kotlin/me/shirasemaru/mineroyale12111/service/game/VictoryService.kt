package me.shirasemaru.mineroyale12111.service.game

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class VictoryService(
    private val plugin: JavaPlugin,
    private val messageService: MessageService
) {

    fun playVictory(winner: Player, onFinished: () -> Unit) {
        teleportAllToWinner(winner)
        messageService.broadcastVictory(winner.name)

        val title = messageService.victoryTitle(winner.name)
        Bukkit.getOnlinePlayers().forEach { it.showTitle(title) }
        repeat(3) { spawnFirework(winner) }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable { onFinished() }, 100L)
    }

    private fun teleportAllToWinner(winner: Player) {
        val location = winner.location.clone().add(0.0, 1.0, 0.0)
        Bukkit.getOnlinePlayers().forEach { it.teleport(location) }
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
