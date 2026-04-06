package me.shirasemaru.mineroyale12111.service.player

import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

class SpectatorService {

    fun applySpectatorMode(player: Player) {
        player.gameMode = GameMode.SPECTATOR
        player.inventory.clear()
    }

    fun refreshTargets(
        spectators: Collection<Player>,
        alivePlayers: Collection<Player>,
        displayNameProvider: (String) -> Component
    ) {
        spectators.forEach { spectator ->
            spectator.inventory.clear()

            alivePlayers.forEachIndexed { index, alivePlayer ->
                spectator.inventory.setItem(index, createPlayerHead(alivePlayer, displayNameProvider))
            }
        }
    }

    fun teleportSpectatorToTarget(spectator: Player, target: Player) {
        spectator.teleport(target.location)
        spectator.playSound(spectator.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
    }

    fun extractSpectateTarget(item: ItemStack): String? {
        val meta = item.itemMeta as? SkullMeta ?: return null
        return meta.owningPlayer?.name
    }

    private fun createPlayerHead(
        target: Player,
        displayNameProvider: (String) -> Component
    ): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta
        meta.owningPlayer = target
        meta.displayName(displayNameProvider(target.name))
        item.itemMeta = meta
        return item
    }
}
