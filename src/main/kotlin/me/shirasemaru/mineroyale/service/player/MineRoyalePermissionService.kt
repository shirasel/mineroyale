package me.shirasemaru.mineroyale.service.player

import me.shirasemaru.mineroyale.command.PermissionNodes
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class MineRoyalePermissionService(
    private val plugin: JavaPlugin
) {

    private val file = File(plugin.dataFolder, "operators.yml")
    private val permissionsByPlayer = linkedMapOf<UUID, MutableSet<String>>()
    private val namesByPlayer = linkedMapOf<UUID, String>()

    init {
        load()
    }

    fun has(player: Player, permission: String): Boolean =
        player.hasPermission(PermissionNodes.ADMIN) ||
            player.hasPermission(permission) ||
            hasInternal(player.uniqueId, PermissionNodes.ADMIN) ||
            hasInternal(player.uniqueId, permission)

    fun grant(player: Player, permission: String) {
        grant(player.uniqueId, player.name, permission)
    }

    fun grant(uuid: UUID, playerName: String, permission: String) {
        val permissions = permissionsByPlayer.getOrPut(uuid) { linkedSetOf() }
        permissions += permission
        namesByPlayer[uuid] = playerName
        save()
    }

    fun revokeAll(player: Player) {
        revokeAll(player.uniqueId)
    }

    fun revokeAll(uuid: UUID) {
        permissionsByPlayer.remove(uuid)
        namesByPlayer.remove(uuid)
        save()
    }

    private fun hasInternal(uuid: UUID, permission: String): Boolean =
        permissionsByPlayer[uuid]?.contains(permission) == true

    private fun load() {
        if (!file.exists()) {
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val operators = config.getConfigurationSection("operators") ?: return

        operators.getKeys(false).forEach { uuidText ->
            val uuid = runCatching { UUID.fromString(uuidText) }.getOrNull() ?: return@forEach
            namesByPlayer[uuid] = operators.getString("$uuidText.name", "") ?: ""
            permissionsByPlayer[uuid] = operators.getStringList("$uuidText.permissions")
                .mapNotNull(PermissionNodes::resolve)
                .toMutableSet()
        }
    }

    private fun save() {
        if (!plugin.dataFolder.exists() && !plugin.dataFolder.mkdirs()) {
            plugin.logger.warning("Failed to create plugin data folder: ${plugin.dataFolder.absolutePath}")
            return
        }

        val config = YamlConfiguration()
        permissionsByPlayer.forEach { (uuid, permissions) ->
            val path = "operators.$uuid"
            config.set("$path.name", namesByPlayer[uuid].orEmpty())
            config.set("$path.permissions", permissions.sorted())
        }
        config.save(file)
    }
}
