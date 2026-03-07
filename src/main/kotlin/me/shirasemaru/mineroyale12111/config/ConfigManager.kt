package me.shirasemaru.mineroyale12111.config

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {

    lateinit var config: FileConfiguration
        private set

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        config = plugin.config
    }

    // =========================
    // Game
    // =========================

    val minPlayers: Int
        get() = config.getInt("game.min-players", 1)

    val maxPlayers: Int
        get() = config.getInt("game.max-players", 20)

    val countdownSeconds: Int
        get() = config.getInt("game.countdown-seconds", 30)

    // =========================
    // World
    // =========================

    val worldName: String
        get() = config.getString("world.name", "world")!!

    val gameWorld: World
        get() = Bukkit.getWorld(worldName)
            ?: throw IllegalStateException("World '$worldName' not found")

    /**
     * ランダム中心抽選範囲（±この値）
     * 例: 2000 → -2000 ～ +2000
     */
    val randomCenterRange: Double
        get() = config.getDouble("world.random-center-range", 2000.0)

    val initialBorderSize: Double
        get() = config.getDouble("world.initial-border-size", 1000.0)

    // BorderManager互換
    val startSize: Double
        get() = initialBorderSize

    // =========================
    // Spawn
    // =========================

    val minSpawnDistance: Double
        get() = config.getDouble("spawn.min-distance", 50.0)

    // =========================
    // Border Basic
    // =========================

    val borderDamagePerSecond: Double
        get() = config.getDouble("border.damage-per-second", 1.0)

    val borderWarningDistance: Int
        get() = config.getInt("border.warning-distance", 10)

    val borderWarningTime: Int
        get() = config.getInt("border.warning-time", 5)

    // =========================
    // Phases
    // =========================

    data class BorderPhase(
        val wait: Int,
        val duration: Int,
        val size: Double
    )

    val borderPhases: List<BorderPhase>
        get() {

            val phaseList = config.getMapList("border.phases")

            if (phaseList.isEmpty()) {
                plugin.logger.warning("No border phases configured.")
                return emptyList()
            }

            return phaseList.mapIndexedNotNull { index, map ->

                val wait = (map["wait"] as? Number)?.toInt() ?: 0
                val duration = (map["duration"] as? Number)?.toInt()
                val size = (map["size"] as? Number)?.toDouble()

                if (duration == null || duration <= 0) {
                    plugin.logger.warning("Invalid duration in border phase ${index + 1}")
                    return@mapIndexedNotNull null
                }

                if (size == null || size <= 0) {
                    plugin.logger.warning("Invalid size in border phase ${index + 1}")
                    return@mapIndexedNotNull null
                }

                BorderPhase(wait, duration, size)
            }
        }

    fun loadBorderPhases(): List<BorderPhase> = borderPhases

    // =========================
    // Final Phase
    // =========================

    val enableFinalSmoothMove: Boolean
        get() = config.getBoolean("border.final-phase.enable-smooth-move", false)

    val finalMoveRange: Double
        get() = config.getDouble("border.final-phase.move-range", 0.0)

    val finalMoveDurationSeconds: Int
        get() = config.getInt("border.final-phase.move-duration-seconds", 0)

    // BorderManager互換
    val enableFinalMove: Boolean
        get() = enableFinalSmoothMove

    val finalMoveDuration: Int
        get() = finalMoveDurationSeconds

    // =========================
    // Enhanced Damage
    // =========================

    val enhancedDamageEnabled: Boolean
        get() = config.getBoolean("border.enhanced-damage.enabled", false)

    val enhancedBaseDamage: Double
        get() = config.getDouble("border.enhanced-damage.base-damage", 1.0)

    val enhancedIncreasePerSecond: Double
        get() = config.getDouble("border.enhanced-damage.increase-per-second", 0.5)

    val enhancedMaxDamage: Double
        get() = config.getDouble("border.enhanced-damage.max-damage", 10.0)

    // =========================
    // Utility
    // =========================

    /**
     * ボーダーサイズのみリセット
     * 中心はBorderManager側でランダム再設定される
     */
    fun resetBorderSize(world: World) {
        world.worldBorder.size = initialBorderSize
    }

    fun reload() {
        plugin.reloadConfig()
        load()
    }
}