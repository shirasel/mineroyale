package me.shirasemaru.mineroyale.config

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {

    lateinit var config: FileConfiguration
        private set

    lateinit var gameSettings: GameSettings
        private set

    lateinit var worldSettings: WorldSettings
        private set

    lateinit var spawnSettings: SpawnSettings
        private set

    lateinit var borderSettings: BorderSettings
        private set

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        config = plugin.config
        gameSettings = loadGameSettings()
        worldSettings = loadWorldSettings()
        spawnSettings = loadSpawnSettings()
        borderSettings = loadBorderSettings()
    }

    val gameWorld: World
        get() = Bukkit.getWorld(worldSettings.name)
            ?: error("World '${worldSettings.name}' not found")

    fun reload() {
        load()
    }

    private fun loadGameSettings(): GameSettings =
        GameSettings(
            minPlayers = config.getInt("game.min-players", 2),
            maxPlayers = config.getInt("game.max-players", 20),
            countdownSeconds = config.getInt("game.countdown-seconds", 30),
            initialPvpGraceSeconds = config.getInt("game.initial-pvp-grace-seconds", 45),
            showPlayerLocatorBar = config.getBoolean("game.show-player-locator-bar", true),
            playerLocatorMaxAlivePlayers = config.getInt("game.player-locator-max-alive-players", 4),
            giveInitialCompass = config.getBoolean("game.give-initial-compass", true),
            giveEndCrystal = config.getBoolean("game.give-end-crystal", true),
            endCrystalGlowSeconds = config.getInt("game.end-crystal-glow-seconds", 15).coerceAtLeast(1),
            endCrystalItemName = config.getString("game.end-crystal-item-name", "発光の岩")!!,
            endCrystalItemDescription = config.getString(
                "game.end-crystal-item-description",
                "使用すると%seconds%秒間自分以外の生存者1名をランダムで発光させます。"
            )!!,
            hideNameTags = config.getBoolean("game.hide-name-tags", false),
            disableAdvancementAnnouncements = config.getBoolean("game.disable-advancement-announcements", false),
            restrictBlockModificationOutsideBorder = config.getBoolean("game.restrict-block-modification-outside-border", false)
        )

    private fun loadWorldSettings(): WorldSettings =
        WorldSettings(
            name = config.getString("world.name", "world")!!,
            randomCenterRange = config.getDouble("world.random-center-range", 2000.0),
            initialBorderSize = config.getDouble("world.initial-border-size", 1000.0)
        )

    private fun loadSpawnSettings(): SpawnSettings =
        SpawnSettings(
            minDistance = config.getDouble("spawn.min-distance", 50.0)
        )

    private fun loadBorderSettings(): BorderSettings {
        val phases = config.getMapList("border.phases").mapIndexedNotNull { index, map ->
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

            PhaseSettings(
                waitSeconds = wait,
                durationSeconds = duration,
                targetSize = size
            )
        }

        return BorderSettings(
            warningDistance = config.getInt("border.warning-distance", 10),
            warningTime = config.getInt("border.warning-time", 5),
            phases = phases,
            finalPhase = FinalPhaseSettings(
                enabled = config.getBoolean("border.final-phase.enable-smooth-move", false),
                moveRange = config.getDouble("border.final-phase.move-range", 0.0),
                moveDurationSeconds = config.getInt("border.final-phase.move-duration-seconds", 0)
            ),
            enhancedDamage = EnhancedDamageSettings(
                enabled = config.getBoolean("border.enhanced-damage.enabled", false),
                baseDamage = config.getDouble("border.enhanced-damage.base-damage", 1.0),
                increasePerSecond = config.getDouble("border.enhanced-damage.increase-per-second", 0.5),
                maxDamage = config.getDouble("border.enhanced-damage.max-damage", 10.0)
            )
        )
    }
}
