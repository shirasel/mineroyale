package me.shirasemaru.mineroyale12111.service.border

import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.service.game.MessageService
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class BorderManager(
    plugin: JavaPlugin,
    private val configManager: ConfigManager,
    messageService: MessageService,
    onPvpStateChanged: (Boolean) -> Unit
) {

    private val borderService = BorderService(plugin, configManager, messageService, onPvpStateChanged)
    private val spawnPointService = SpawnPointService(configManager)
    private val borderDamageService = BorderDamageService(plugin, configManager)

    private val world get() = configManager.gameWorld
    private val border get() = world.worldBorder

    fun initialize(session: GameSession) {
        borderService.initialize(session, world, border)
        borderDamageService.start(world, border)
    }

    fun isPvpEnabled(): Boolean = borderService.isPvpEnabled()

    fun generateRandomSpawnLocations(players: List<Player>) =
        spawnPointService.generateRandomSpawnLocations(players, border)

    fun runPhases(session: GameSession, onComplete: () -> Unit) {
        borderService.runPhases(session, border, onComplete)
    }

    fun stop() {
        borderService.stop()
        borderDamageService.stop()
    }

    fun reset(session: GameSession) {
        borderService.reset(session, border)
        borderDamageService.stop()
    }
}
