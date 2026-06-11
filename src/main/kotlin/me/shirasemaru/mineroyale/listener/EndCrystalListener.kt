package me.shirasemaru.mineroyale.listener

import me.shirasemaru.mineroyale.game.GameManager
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class EndCrystalListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        val player = event.player

        if (!gameManager.isRunning()) return
        if (player.gameMode != GameMode.SURVIVAL) return
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.RIGHT_CLICK_BLOCK
        ) return

        val item = event.item ?: return
        if (!gameManager.isEndCrystal(item)) return

        event.isCancelled = true

        if (!gameManager.useEndCrystal(player)) return

        if (item.amount > 1) {
            item.amount -= 1
        } else {
            player.inventory.setItemInMainHand(null)
        }
    }
}
