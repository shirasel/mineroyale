package me.shirasemaru.mineroyale12111.bootstrap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import me.shirasemaru.mineroyale12111.Mineroyale12111
import me.shirasemaru.mineroyale12111.coroutines.BukkitDispatcher
import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.game.MatchScopeFactory
import me.shirasemaru.mineroyale12111.game.MatchScopeHolder
import me.shirasemaru.mineroyale12111.service.border.BorderManager
import me.shirasemaru.mineroyale12111.service.game.CountdownService
import me.shirasemaru.mineroyale12111.service.game.DeathMarkerService
import me.shirasemaru.mineroyale12111.service.game.MatchFlowService
import me.shirasemaru.mineroyale12111.service.game.MatchLifecycleService
import me.shirasemaru.mineroyale12111.service.game.MessageService
import me.shirasemaru.mineroyale12111.service.game.VictoryService
import me.shirasemaru.mineroyale12111.service.item.EndCrystalService
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import me.shirasemaru.mineroyale12111.service.player.PlayerSetupService
import me.shirasemaru.mineroyale12111.service.player.SpectatorService
import me.shirasemaru.mineroyale12111.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager

class PluginScope private constructor(
    val configManager: me.shirasemaru.mineroyale12111.config.ConfigManager,
    val messageService: MessageService,
    val worldProvider: GameWorldProvider,
    val scoreboardFactory: ScoreboardFactory,
    val scoreboardManager: ScoreboardManager,
    val playerRegistry: PlayerRegistry,
    val playerSetupService: PlayerSetupService,
    val spectatorService: SpectatorService,
    val countdownService: CountdownService,
    val matchFlowService: MatchFlowService,
    val victoryService: VictoryService,
    val compassTrackingService: CompassTrackingService,
    val endCrystalService: EndCrystalService,
    val deathMarkerService: DeathMarkerService,
    val borderManager: BorderManager,
    val matchScopeFactory: MatchScopeFactory,
    val matchScopeHolder: MatchScopeHolder,
    val coroutineScope: CoroutineScope,
    val matchLifecycleService: MatchLifecycleService,
    val gameManager: GameManager
) {

    companion object {
        fun create(plugin: Mineroyale12111): PluginScope {
            val configManager = me.shirasemaru.mineroyale12111.config.ConfigManager(plugin).apply { load() }
            val messageService = MessageService()
            val worldProvider = BukkitGameWorldProvider(configManager)
            val scoreboardFactory = BukkitScoreboardFactory()
            val scoreboardManager = scoreboardFactory.create()
            val playerRegistry = PlayerRegistry()
            val playerSetupService = PlayerSetupService(configManager)
            val spectatorService = SpectatorService()
            val countdownService = CountdownService(plugin)
            val matchFlowService = MatchFlowService()
            val victoryService = VictoryService(plugin, messageService)
            val compassTrackingService = CompassTrackingService(plugin, configManager)
            val endCrystalService = EndCrystalService(plugin, configManager, messageService)
            val deathMarkerService = DeathMarkerService(plugin)
            val matchScopeFactory = MatchScopeFactory()
            val matchScopeHolder = MatchScopeHolder(matchScopeFactory.create())
            val coroutineScope = CoroutineScope(SupervisorJob() + BukkitDispatcher(plugin))
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
                worldProvider = worldProvider,
                scoreboardFactory = scoreboardFactory,
                scoreboardManager = scoreboardManager,
                playerRegistry = playerRegistry,
                playerSetupService = playerSetupService,
                spectatorService = spectatorService,
                countdownService = countdownService,
                matchFlowService = matchFlowService,
                victoryService = victoryService,
                compassTrackingService = compassTrackingService,
                endCrystalService = endCrystalService,
                deathMarkerService = deathMarkerService,
                borderManager = borderManager,
                matchScopeFactory = matchScopeFactory,
                matchScopeHolder = matchScopeHolder,
                coroutineScope = coroutineScope,
                matchLifecycleService = matchLifecycleService,
                gameManager = gameManager
            )
        }
    }
}
