package me.shirasemaru.mineroyale12111.service.game

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class DeathMarkerService(
    private val plugin: JavaPlugin
) {

    private val markerIds = linkedSetOf<UUID>()

    fun spawnMarker(player: Player, location: Location) {
        val world = location.world ?: return
        val spawnLocation = location.clone().apply {
            x = blockX + 0.5
            z = blockZ + 0.5
        }

        val armorStand = world.spawn(spawnLocation, ArmorStand::class.java)
        armorStand.setGravity(false)
        armorStand.isInvulnerable = true
        armorStand.isVisible = true
        armorStand.setBasePlate(false)
        armorStand.setArms(false)
        armorStand.equipment.helmet = createPlayerHead(player)
        markerIds += armorStand.uniqueId
    }

    fun clearMarkers() {
        markerIds.toList().forEach { uuid ->
            val entity = plugin.server.getEntity(uuid)
            entity?.remove()
        }
        markerIds.clear()
    }

    fun isMarker(entity: Entity): Boolean =
        markerIds.contains(entity.uniqueId)

    private fun createPlayerHead(player: OfflinePlayer): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = player
        item.itemMeta = meta
        return item
    }
}
