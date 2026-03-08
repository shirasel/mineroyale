package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

class GameListener(
    private val gameManager: GameManager
) : Listener {

    /*
     * プレイヤー死亡
     * 1tick遅らせて処理（リスポーン不能バグ対策）
     */
    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {

        if (!gameManager.isRunning()) return

        val player = event.entity

        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().plugins[0],
            Runnable {
                gameManager.handlePlayerDeath(player)
            },
            1L
        )
    }

    /*
     * プレイヤー退出
     */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {

        if (!gameManager.isRunning()) return

        gameManager.handlePlayerDeath(event.player)
    }

    /*
     * キック
     */
    @EventHandler
    fun onKick(event: PlayerKickEvent) {

        if (!gameManager.isRunning()) return

        gameManager.handlePlayerDeath(event.player)
    }

    /*
     * PvP制御
     * 弓・トライデント対応
     */
    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {

        if (!gameManager.isRunning()) return

        val victim = event.entity
        if (victim !is Player) return

        val attacker: Player? = when (val damager = event.damager) {

            is Player -> damager

            is Projectile -> damager.shooter as? Player

            else -> null
        }

        if (attacker == null) return

        if (!gameManager.isPvpEnabled()) {
            event.isCancelled = true
        }
    }
}