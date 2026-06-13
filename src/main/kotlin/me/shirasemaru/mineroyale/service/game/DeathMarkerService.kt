package me.shirasemaru.mineroyale.service.game

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class DeathMarkerService(
    private val plugin: JavaPlugin
) {

    private val markerKey = NamespacedKey(plugin, "death_marker")
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
        armorStand.equipment.setHelmet(createPlayerHead(player))
        armorStand.persistentDataContainer.set(markerKey, PersistentDataType.BYTE, 1)
        markerIds += armorStand.uniqueId
    }

    fun clearMarkers() {
        markerIds.toList().forEach { uuid ->
            val entity = plugin.server.getEntity(uuid)
            entity?.remove()
        }
        plugin.server.worlds
            .flatMap { it.entities }
            .filter(::isTaggedMarker)
            .forEach(Entity::remove)
        markerIds.clear()
    }

    fun isMarker(entity: Entity): Boolean =
        markerIds.contains(entity.uniqueId) || isTaggedMarker(entity)

    private fun isTaggedMarker(entity: Entity): Boolean =
        entity.persistentDataContainer.has(markerKey, PersistentDataType.BYTE)

    private fun createPlayerHead(player: OfflinePlayer): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = player
        item.itemMeta = meta
        return item
    }
}
