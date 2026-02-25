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

        // スコアボード初期化（他プラグイン干渉対策）
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard

        when (gameManager.state) {

            GameState.WAITING -> {
                player.gameMode = GameMode.SURVIVAL
                player.health = player.maxHealth
                player.foodLevel = 20
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

                player.sendMessage("§aゲーム待機中です。開始をお待ちください。")
            }

            GameState.RUNNING -> {
                // ゲーム途中参加は強制観戦
                player.gameMode = GameMode.SPECTATOR
                player.health = player.maxHealth
                player.foodLevel = 20
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

                player.sendMessage("§c現在ゲーム進行中のため観戦モードになりました。")
            }

            GameState.ENDING -> {
                // リセットタイム
                player.gameMode = GameMode.SURVIVAL
                player.health = player.maxHealth
                player.foodLevel = 20
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

                player.sendMessage("§eゲーム終了処理中です。まもなく次のゲームが開始されます。")
            }
        }
    }
}