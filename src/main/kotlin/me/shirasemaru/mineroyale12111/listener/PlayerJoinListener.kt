package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.game.GameState
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // とりあえずメインスコアボードを適用（初期化）
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard

        when (gameManager.state) {
            GameState.WAITING,
            GameState.ENDING -> {
                player.gameMode = GameMode.SURVIVAL
            }

            GameState.RUNNING -> {
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("§c現在ゲーム中のため観戦モードです。")
            }
        }
    }
}
