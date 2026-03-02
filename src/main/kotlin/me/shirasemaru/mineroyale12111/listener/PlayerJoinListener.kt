package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
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

        // 共通初期化処理
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = maxHealth
        player.foodLevel = 20
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

        if (gameManager.isRunning()) {

            // ゲーム途中参加 → 観戦
            player.gameMode = GameMode.SPECTATOR
            player.sendMessage("§c現在ゲーム進行中のため観戦モードになりました。")

        } else {

            // WAITING状態
            player.gameMode = GameMode.SURVIVAL
            player.sendMessage("§aゲーム待機中です。開始をお待ちください。")
        }
    }
}