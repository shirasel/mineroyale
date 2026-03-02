package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent

/**
 * ゲーム中のみ自然回復速度を調整するリスナー
 */
class HealthRegainListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onHealthRegain(event: EntityRegainHealthEvent) {

        if (event.entity !is Player) return
        if (!gameManager.isRunning()) return

        // 自然回復（満腹回復）のみ調整
        if (event.regainReason == EntityRegainHealthEvent.RegainReason.SATIATED) {
            // 回復量を2/3にして1.5倍遅く
            event.amount *= 2.0 / 3.0
        }
    }
}
