package me.shirasemaru.mineroyale12111.service.item

import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.service.game.MessageService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

class EndCrystalService(
    plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val messageService: MessageService
) {

    private val itemKey = NamespacedKey(plugin, "end_crystal_marker")

    fun distribute(players: Collection<Player>) {
        if (!configManager.gameSettings.giveEndCrystal) return

        players.forEach { player ->
            player.inventory.addItem(createItem())
            messageService.sendEndCrystalReceived(player)
        }
    }

    fun isEndCrystal(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.END_CRYSTAL) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }

    fun use(user: Player, alivePlayers: List<Player>): Boolean {
        val candidates = alivePlayers.filter { it.isOnline && it.uniqueId != user.uniqueId }
        val target = candidates.randomOrNull(Random) ?: run {
            messageService.sendEndCrystalNoTarget(user)
            return false
        }
        val glowSeconds = configManager.gameSettings.endCrystalGlowSeconds

        target.addPotionEffect(
            PotionEffect(
                PotionEffectType.GLOWING,
                glowSeconds * 20,
                0,
                false,
                true,
                true
            )
        )
        playUseSound(user)
        playUseSound(target)
        messageService.sendEndCrystalUsed(user, target.name, glowSeconds)
        messageService.sendEndCrystalMarked(target, glowSeconds)
        return true
    }

    private fun createItem(): ItemStack {
        val settings = configManager.gameSettings
        val glowSeconds = settings.endCrystalGlowSeconds
        val item = ItemStack(Material.END_CRYSTAL)
        val meta = item.itemMeta

        meta.displayName(Component.text(settings.endCrystalItemName, NamedTextColor.LIGHT_PURPLE))
        meta.lore(
            listOf(
                Component.text("右クリックで使用", NamedTextColor.GRAY),
                Component.text(
                    settings.endCrystalItemDescription.replace("%seconds%", glowSeconds.toString()),
                    NamedTextColor.AQUA
                )
            )
        )
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    private fun playUseSound(player: Player) {
        player.playSound(player.location, Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.15f)
    }
}
