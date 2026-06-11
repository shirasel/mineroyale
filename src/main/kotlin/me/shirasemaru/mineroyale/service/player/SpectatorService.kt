package me.shirasemaru.mineroyale.service.player

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

class SpectatorService {

    private val navigatorTitle = Component.text("観戦先選択")

    fun applySpectatorMode(player: Player) {
        player.gameMode = GameMode.SPECTATOR
        player.inventory.clear()
        player.inventory.setItem(0, createNavigatorRod())
    }

    fun refreshTargets(
        spectators: Collection<Player>,
        alivePlayers: Collection<Player>,
        displayNameProvider: (String) -> Component
    ) {
        spectators.forEach { spectator ->
            spectator.inventory.setItem(0, createNavigatorRod())

            if (isSpectatorMenu(spectator.openInventory.title())) {
                openTeleportMenu(spectator, alivePlayers, displayNameProvider)
            }
        }
    }

    fun openTeleportMenu(
        spectator: Player,
        alivePlayers: Collection<Player>,
        displayNameProvider: (String) -> Component
    ) {
        val size = ((alivePlayers.size.coerceAtLeast(1) - 1) / 9 + 1).coerceAtMost(6) * 9
        val inventory = Bukkit.createInventory(null, size, navigatorTitle)

        alivePlayers.forEachIndexed { index, alivePlayer ->
            if (index < size) {
                inventory.setItem(index, createPlayerHead(alivePlayer, displayNameProvider))
            }
        }

        spectator.openInventory(inventory)
    }

    fun teleportSpectatorToTarget(spectator: Player, target: Player) {
        spectator.teleport(target.location)
        spectator.playSound(spectator.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
    }

    fun extractSpectateTarget(item: ItemStack): String? {
        val meta = item.itemMeta as? SkullMeta ?: return null
        return meta.owningPlayer?.name
    }

    fun isNavigatorRod(item: ItemStack?): Boolean =
        item != null && item.type == Material.BLAZE_ROD

    fun isSpectatorMenu(title: Component): Boolean = title == navigatorTitle

    private fun createNavigatorRod(): ItemStack {
        val item = ItemStack(Material.BLAZE_ROD)
        val meta = item.itemMeta
        meta.displayName(Component.text("観戦メニュー", NamedTextColor.GOLD))
        meta.lore(listOf(Component.text("右クリックで観戦先一覧を開く", NamedTextColor.GRAY)))
        item.itemMeta = meta
        return item
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
