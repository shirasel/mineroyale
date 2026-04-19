package me.shirasemaru.mineroyale12111.game

import me.shirasemaru.mineroyale12111.Mineroyale12111
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.service.border.BorderManager
import me.shirasemaru.mineroyale12111.service.game.CountdownService
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
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GameManager(
    private val plugin: Mineroyale12111,
    private val configManager: ConfigManager,
    private val playerRegistry: PlayerRegistry,
    private val playerSetupService: PlayerSetupService,
    private val spectatorService: SpectatorService,
    private val countdownService: CountdownService,
    private val messageService: MessageService,
    scoreboardManager: ScoreboardManager,
    victoryService: VictoryService,
    compassTrackingService: CompassTrackingService,
    private val endCrystalService: EndCrystalService = EndCrystalService(plugin, configManager, messageService)
) {

    private val session = GameSession()
    private val matchFlowService = MatchFlowService()
    private val borderManager = BorderManager(plugin, configManager, messageService) { session.pvpEnabled = it }
    private val matchLifecycleService = MatchLifecycleService(
        plugin = plugin,
        configManager = configManager,
        scoreboardManager = scoreboardManager,
        playerRegistry = playerRegistry,
        playerSetupService = playerSetupService,
        borderManager = borderManager,
        compassTrackingService = compassTrackingService,
        victoryService = victoryService,
        messageService = messageService,
        matchFlowService = matchFlowService
    )

    fun getState(): GameState = session.state

    fun isRunning(): Boolean = session.state == GameState.RUNNING

    fun isPvpEnabled(): Boolean = session.pvpEnabled

    fun canStartNewGame(): Boolean =
        matchFlowService.evaluateStart(
            session = session,
            playerCount = matchLifecycleService.getEligiblePlayers().size,
            minPlayers = configManager.gameSettings.minPlayers,
            maxPlayers = configManager.gameSettings.maxPlayers
        ) == MatchFlowService.StartCheckResult.Ready

    fun canStopGame(): Boolean = matchFlowService.canStop(session)

    fun startGame() {
        val players = matchLifecycleService.getEligiblePlayers()
        session.participantCount = players.size

        when (
            matchFlowService.evaluateStart(
                session = session,
                playerCount = players.size,
                minPlayers = configManager.gameSettings.minPlayers,
                maxPlayers = configManager.gameSettings.maxPlayers
            )
        ) {
            MatchFlowService.StartCheckResult.Ready -> {
                matchFlowService.moveToCountdown(session)
                startCountdown()
            }

            MatchFlowService.StartCheckResult.AlreadyInProgress -> return

            MatchFlowService.StartCheckResult.NotEnoughPlayers -> {
                messageService.broadcastNotEnoughPlayersToStart()
            }

            MatchFlowService.StartCheckResult.TooManyPlayers -> {
                messageService.broadcastTooManyPlayers(configManager.gameSettings.maxPlayers)
            }
        }
    }

    fun stopGame() {
        if (!matchFlowService.canStop(session)) return

        countdownService.cancel(session)
        matchLifecycleService.stopCurrentMatch(session)
    }

    private fun startCountdown() {
        countdownService.start(
            session = session,
            seconds = configManager.gameSettings.countdownSeconds,
            minPlayers = configManager.gameSettings.minPlayers,
            participantProvider = matchLifecycleService::getEligiblePlayers,
            onTick = { time, players ->
                matchLifecycleService.prepareSpawnLocations(players)
                if (time <= 5 || time % 10 == 0) {
                    messageService.broadcastCountdown(time, players)
                }
            },
            onCancelled = {
                matchLifecycleService.discardPreparedSpawnLocations()
                messageService.broadcastCountdownCancelled()
                matchFlowService.cancelCountdown(session)
            },
            onCompleted = { players ->
                messageService.broadcastGameStarting()
                matchLifecycleService.startMatch(session, players) {
                    endGame(null)
                }
                endCrystalService.distribute(players)
            }
        )
    }

    fun endGame(winner: Player?) {
        if (!matchFlowService.canFinish(session)) return

        countdownService.cancel(session)
        matchLifecycleService.finishMatch(session, winner)
    }

    fun handlePlayerDeath(player: Player) {
        when (val result = matchFlowService.processElimination(session, playerRegistry, player)) {
            MatchFlowService.EliminationResult.Ignored -> return

            is MatchFlowService.EliminationResult.Eliminated -> {
                session.aliveCount = result.aliveCount

                spectatorService.applySpectatorMode(player)
                spectatorService.refreshTargets(
                    spectators = playerRegistry.getSpectators(),
                    alivePlayers = playerRegistry.getAlivePlayers(),
                    displayNameProvider = messageService::spectatorHeadDisplayName
                )

                messageService.broadcastPlayerEliminated(player.name, result.aliveCount)

                if (result.aliveCount <= 1) {
                    endGame(result.winner)
                }
            }
        }
    }

    fun reloadConfig() {
        configManager.reload()
    }

    fun prepareLobbyPlayer(player: Player) {
        playerSetupService.preparePlayerForLobby(player)
        messageService.sendLobbyWaitingMessage(player)
    }

    fun handleLateJoin(player: Player) {
        playerSetupService.prepareLateJoinSpectator(player)
        playerRegistry.addSpectator(player)
        spectatorService.refreshTargets(
            spectators = playerRegistry.getSpectators(),
            alivePlayers = playerRegistry.getAlivePlayers(),
            displayNameProvider = messageService::spectatorHeadDisplayName
        )
        messageService.sendLateJoinSpectatorMessage(player)
    }

    fun isAlive(player: Player): Boolean = playerRegistry.isAlive(player)

    fun isSpectator(player: Player): Boolean = playerRegistry.isSpectator(player)

    fun reapplySpectatorMode(player: Player) {
        spectatorService.applySpectatorMode(player)
    }

    fun openSpectatorMenu(player: Player) {
        spectatorService.openTeleportMenu(
            spectator = player,
            alivePlayers = playerRegistry.getAlivePlayers(),
            displayNameProvider = messageService::spectatorHeadDisplayName
        )
    }

    fun isOutsideCurrentBorder(location: Location): Boolean =
        borderManager.isOutsideBorder(location)

    fun teleportSpectator(spectator: Player, target: Player) {
        spectatorService.teleportSpectatorToTarget(spectator, target)
        messageService.sendSpectatorTargetChanged(spectator, target.name)
    }

    fun extractSpectatorTargetName(item: ItemStack): String? =
        spectatorService.extractSpectateTarget(item)

    fun isSpectatorNavigator(item: ItemStack): Boolean =
        spectatorService.isNavigatorRod(item)

    fun isSpectatorMenu(title: String): Boolean =
        spectatorService.isSpectatorMenu(title)

    fun isEndCrystal(item: ItemStack): Boolean =
        endCrystalService.isEndCrystal(item)

    fun useEndCrystal(player: Player): Boolean =
        endCrystalService.use(player, playerRegistry.getAlivePlayers())
}
