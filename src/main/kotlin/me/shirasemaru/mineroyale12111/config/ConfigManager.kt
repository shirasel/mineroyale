package me.shirasemaru.mineroyale12111.config

import me.shirasemaru.mineroyale12111.game.BorderPhase
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {

    private val config: FileConfiguration
        get() = plugin.config

    val countdownSeconds: Int
        get() = config.getInt("countdown-seconds", 30)

    val gameDurationSeconds: Long
        get() = config.getLong("game-duration-seconds", 1800L)

    val teleportRange: Int
        get() = config.getInt("teleportRange", 500000)

    val teleportCenterX: Double
        get() = config.getDouble("teleport-center-x", 0.0)

    val teleportCenterZ: Double
        get() = config.getDouble("teleport-center-z", 0.0)

    val teleportRadius: Int
        get() = config.getInt("teleport-radius", 1000)

    val deathInventoryUpdateDelayTicks: Long
        get() = config.getLong("deathInventoryUpdateDelayTicks", 20L)

    val borderCenterX: Double
        get() = config.getDouble("world-border.center.x", 0.0)

    val borderCenterZ: Double
        get() = config.getDouble("world-border.center.z", 0.0)

    fun loadBorderPhases(): List<BorderPhase> {

        val section = config.getConfigurationSection("world-border.phases")
            ?: return emptyList()

        return section.getKeys(false)
            .mapNotNull { key ->
                val phaseNumber = key.toIntOrNull() ?: return@mapNotNull null
                val phaseSection = section.getConfigurationSection(key) ?: return@mapNotNull null

                val startSize = phaseSection.getDouble("start-size")
                val endSize = phaseSection.getDouble("end-size")
                val duration = phaseSection.getLong("duration")

                // バリデーション
                if (startSize <= 0 || endSize <= 0 || duration <= 0) {
                    plugin.logger.warning("BorderPhase $phaseNumber に無効な値があります。スキップします。")
                    return@mapNotNull null
                }

                if (startSize < endSize) {
                    plugin.logger.warning("BorderPhase $phaseNumber は拡大設定になっています。スキップします。")
                    return@mapNotNull null
                }

                BorderPhase(phaseNumber, startSize, endSize, duration)
            }
            .sortedBy { it.phaseNumber }
    }

    fun reload() {
        plugin.reloadConfig()
        plugin.logger.info("config.yml をリロードしました。")
    }
}
