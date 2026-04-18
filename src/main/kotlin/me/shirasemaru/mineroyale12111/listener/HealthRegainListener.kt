package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import java.util.UUID

class HealthRegainListener(
    private val gameManager: GameManager,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : Listener {

    private val lastNaturalRegenAt = mutableMapOf<UUID, Long>()

    @EventHandler
    fun onHealthRegain(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        if (!gameManager.isRunning()) return

        player.foodLevel = 20
        player.saturation = 20f

        if (event.regainReason != EntityRegainHealthEvent.RegainReason.SATIATED) return

        val now = currentTimeMillis()
        val lastRegenAt = lastNaturalRegenAt[player.uniqueId]

        if (lastRegenAt != null && now - lastRegenAt < NATURAL_REGEN_COOLDOWN_MILLIS) {
            event.isCancelled = true
            return
        }

        lastNaturalRegenAt[player.uniqueId] = now
        event.amount = NATURAL_REGEN_AMOUNT
    }

    private companion object {
        const val NATURAL_REGEN_COOLDOWN_MILLIS = 5000L
        const val NATURAL_REGEN_AMOUNT = 0.5
    }
}
