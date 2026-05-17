package me.shirasemaru.mineroyale12111.service.border

import kotlinx.coroutines.CoroutineScope
import me.shirasemaru.mineroyale12111.bootstrap.GameWorldProvider
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.service.game.MessageService
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class BorderManager(
    plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val worldProvider: GameWorldProvider,
    messageService: MessageService,
    onPvpStateChanged: (Boolean) -> Unit,
    private val aliveProvider: () -> List<Player>,
    coroutineScope: CoroutineScope
) {

    private val borderService = BorderService(plugin, configManager, messageService, onPvpStateChanged, coroutineScope)
    private val spawnPointService = SpawnPointService(configManager, worldProvider)
    private val borderDamageService = BorderDamageService(plugin, configManager)

    private val world get() = worldProvider.require()
    private val border get() = world.worldBorder

    fun initialize(session: GameSession) {
        borderService.initialize(session, world, border)
        borderDamageService.start(world, border, aliveProvider)
    }

    fun initialize(session: GameSession, plan: MatchBorderPlan) {
        borderService.initialize(session, world, border, plan)
        borderDamageService.start(world, border, aliveProvider)
    }

    fun isPvpEnabled(): Boolean = borderService.isPvpEnabled()

    fun generateRandomSpawnLocations(players: List<Player>) =
        spawnPointService.generateRandomSpawnLocations(players, border)

    fun createInitialBorderPlan(): MatchBorderPlan =
        borderService.createInitialBorderPlan()

    fun generateRandomSpawnLocations(players: List<Player>, plan: MatchBorderPlan) =
        spawnPointService.generateRandomSpawnLocations(players, plan)

    fun runPhases(session: GameSession, onComplete: () -> Unit) {
        borderService.runPhases(session, border, onComplete)
    }

    fun stop() {
        borderService.stop()
        borderDamageService.stop()
    }

    fun isOutsideBorder(location: Location): Boolean {
        if (location.world?.name != world.name) {
            return false
        }

        val center = border.center
        val radius = border.size / 2
        val dx = kotlin.math.abs(location.x - center.x)
        val dz = kotlin.math.abs(location.z - center.z)
        return dx > radius || dz > radius
    }

    fun reset(session: GameSession) {
        borderService.reset(session, border)
        borderDamageService.stop()
    }

    fun observeBorderDamageTarget(player: Player, isAlive: Boolean) {
        borderDamageService.observePlayer(player, border, isAlive)
    }
}
