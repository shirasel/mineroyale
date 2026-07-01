package me.shirasemaru.mineroyale.bootstrap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import me.shirasemaru.mineroyale.Mineroyale
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.coroutines.BukkitDispatcher
import me.shirasemaru.mineroyale.game.GameManager
import me.shirasemaru.mineroyale.game.MatchScopeFactory
import me.shirasemaru.mineroyale.game.MatchScopeHolder
import me.shirasemaru.mineroyale.service.border.BorderManager
import me.shirasemaru.mineroyale.service.game.CountdownService
import me.shirasemaru.mineroyale.service.game.DeathMarkerService
import me.shirasemaru.mineroyale.service.game.MatchFlowService
import me.shirasemaru.mineroyale.service.game.MatchLifecycleService
import me.shirasemaru.mineroyale.service.game.MessageService
import me.shirasemaru.mineroyale.service.game.VictoryService
import me.shirasemaru.mineroyale.service.item.EndCrystalService
import me.shirasemaru.mineroyale.service.player.MineRoyalePermissionService
import me.shirasemaru.mineroyale.service.player.PlayerRegistry
import me.shirasemaru.mineroyale.service.player.PlayerSetupService
import me.shirasemaru.mineroyale.service.player.SpectatorService
import me.shirasemaru.mineroyale.service.tracking.CompassTrackingService

class PluginScope private constructor(
    val configManager: ConfigManager,
    val messageService: MessageService,
    val permissionService: MineRoyalePermissionService,
    val coroutineScope: CoroutineScope,
    val gameManager: GameManager
) {

    companion object {
        fun create(plugin: Mineroyale): PluginScope {
            val configManager = ConfigManager(plugin).apply { load() }
            val messageService = MessageService()
            val permissionService = MineRoyalePermissionService(plugin)
            val worldProvider = BukkitGameWorldProvider(configManager)
            worldProvider.require()
            val scoreboardManager = BukkitScoreboardFactory().create()
            val playerRegistry = PlayerRegistry()
            val playerSetupService = PlayerSetupService(configManager)
            val spectatorService = SpectatorService(plugin, messageService)
            val countdownService = CountdownService(plugin)
            val matchFlowService = MatchFlowService()
            val victoryService = VictoryService(plugin, messageService)
            val endCrystalService = EndCrystalService(plugin, configManager, messageService)
            val deathMarkerService = DeathMarkerService(plugin)
            deathMarkerService.clearMarkers()
            val matchScopeFactory = MatchScopeFactory()
            val matchScopeHolder = MatchScopeHolder(matchScopeFactory.create())
            val coroutineScope = CoroutineScope(SupervisorJob() + BukkitDispatcher(plugin))
            val compassTrackingService = CompassTrackingService(plugin, configManager, coroutineScope)
            val borderManager = BorderManager(
                plugin = plugin,
                configManager = configManager,
                worldProvider = worldProvider,
                messageService = messageService,
                onPvpStateChanged = { matchScopeHolder.current.session.pvpEnabled = it },
                aliveProvider = playerRegistry::getAlivePlayers,
                coroutineScope = coroutineScope
            )
            val matchLifecycleService = MatchLifecycleService(
                plugin = plugin,
                configManager = configManager,
                worldProvider = worldProvider,
                scoreboardManager = scoreboardManager,
                playerRegistry = playerRegistry,
                playerSetupService = playerSetupService,
                borderManager = borderManager,
                compassTrackingService = compassTrackingService,
                victoryService = victoryService,
                deathMarkerService = deathMarkerService,
                messageService = messageService,
                matchFlowService = matchFlowService,
                matchScopeFactory = matchScopeFactory,
                matchScopeHolder = matchScopeHolder
            )
            val gameManager = GameManager(
                plugin = plugin,
                configManager = configManager,
                playerRegistry = playerRegistry,
                playerSetupService = playerSetupService,
                spectatorService = spectatorService,
                countdownService = countdownService,
                messageService = messageService,
                matchFlowService = matchFlowService,
                matchLifecycleService = matchLifecycleService,
                borderManager = borderManager,
                endCrystalService = endCrystalService,
                deathMarkerService = deathMarkerService,
                matchScopeHolder = matchScopeHolder,
                coroutineScope = coroutineScope
            )

            return PluginScope(
                configManager = configManager,
                messageService = messageService,
                permissionService = permissionService,
                coroutineScope = coroutineScope,
                gameManager = gameManager
            )
        }
    }
}
