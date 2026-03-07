package me.shirasemaru.mineroyale12111.listener

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.meta.SkullMeta

class SpectatorListener(
    private val gameManager: GameManager
) : Listener {

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {

        val player = event.player

        // ゲーム中のみ
        if (!gameManager.isRunning()) return

        // 観戦者のみ
        if (player.gameMode != GameMode.SPECTATOR) return

        // 右クリックのみ
        if (event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.RIGHT_CLICK_BLOCK) return

        val item = event.item ?: return
        if (item.type != Material.PLAYER_HEAD) return

        val meta = item.itemMeta as? SkullMeta ?: return
        val target = meta.owningPlayer ?: return

        val targetPlayer = Bukkit.getPlayer(target.uniqueId) ?: return
        if (!targetPlayer.isOnline) return

        event.isCancelled = true

        player.teleport(targetPlayer.location)

        player.sendMessage("§e${targetPlayer.name} を観戦しています")
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
    }
}