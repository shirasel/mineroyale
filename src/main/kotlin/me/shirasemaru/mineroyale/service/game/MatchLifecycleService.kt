package me.shirasemaru.mineroyale.service.game

import me.shirasemaru.mineroyale.bootstrap.GameWorldProvider
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.coroutines.awaitChunkPreload
import me.shirasemaru.mineroyale.coroutines.nextTick
import me.shirasemaru.mineroyale.game.MatchScope
import me.shirasemaru.mineroyale.game.MatchScopeFactory
import me.shirasemaru.mineroyale.game.MatchScopeHolder
import me.shirasemaru.mineroyale.game.GameSession
import me.shirasemaru.mineroyale.game.MatchRuleSnapshot
import me.shirasemaru.mineroyale.service.border.BorderManager
import me.shirasemaru.mineroyale.service.border.MatchBorderPlan
import me.shirasemaru.mineroyale.service.player.PlayerRegistry
import me.shirasemaru.mineroyale.service.player.PlayerSetupService
import me.shirasemaru.mineroyale.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale.ui.ScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class MatchLifecycleService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val worldProvider: GameWorldProvider,
    private val scoreboardManager: ScoreboardManager,
    private val playerRegistry: PlayerRegistry,
    private val playerSetupService: PlayerSetupService,
    private val borderManager: BorderManager,
    private val compassTrackingService: CompassTrackingService,
    private val victoryService: VictoryService,
    private val deathMarkerService: DeathMarkerService,
    private val messageService: MessageService,
    private val matchFlowService: MatchFlowService,
    private val matchScopeFactory: MatchScopeFactory,
    private val matchScopeHolder: MatchScopeHolder
) {

    private companion object {
        const val MATCH_START_PLAYER_BATCH_SIZE = 4
    }

    private val matchScope: MatchScope
        get() = matchScopeHolder.current

    fun setVictoryRespawnLocation(location: Location?) {
        matchScope.victoryRespawnLocation = location
    }

    fun clearVictoryRespawnLocation() {
        matchScope.victoryRespawnLocation = null
    }

    fun respawnOverrideLocation(): Location? =
        matchScope.victoryRespawnLocation?.clone()

    fun getEligiblePlayers(): List<Player> =
        Bukkit.getOnlinePlayers()
            .filter { !playerRegistry.isSpectator(it) }
            .sortedBy { it.name }

    fun prepareSpawnLocations(players: List<Player>) {
        if (players.isEmpty()) {
            clearPreparedSpawnLocations()
            return
        }

        val playerIds = players.map { it.uniqueId }
        if (matchScope.preparedSpawnPlayerIds == playerIds && matchScope.preparedSpawnLocations?.size == players.size) {
            return
        }

        val borderPlan = borderManager.createInitialBorderPlan()
        matchScope.preparedBorderPlan = borderPlan
        matchScope.preparedSpawnLocations = borderManager.generateRandomSpawnLocations(players, borderPlan)
        matchScope.preparedSpawnPlayerIds = playerIds
    }

    fun discardPreparedSpawnLocations() {
        clearPreparedSpawnLocations()
    }

    suspend fun startMatch(
        session: GameSession,
        players: List<Player>,
        onPlayersReady: () -> Unit = {},
        onMatchComplete: () -> Unit
    ) {
        deathMarkerService.clearMarkers()
        matchFlowService.moveToRunning(session)
        playerRegistry.resetForMatch(players)
        session.participantCount = players.size
        session.aliveCount = players.size

        applyMatchRules()
        val borderPlan = consumePreparedBorderPlan()
        val spawnMap = consumePreparedSpawnLocations(players, borderPlan)

        awaitChunkPreload(spawnMap.values)
        plugin.nextTick()
        borderManager.initialize(session, borderPlan)
        prepareMatchPlayersInBatches(spawnMap)
        plugin.nextTick()
        onPlayersReady()
        borderManager.runPhases(session, onMatchComplete)
        startScoreboardTask(session)
        compassTrackingService.start(playerRegistry::getAlivePlayers)
        messageService.logMatchStarted(plugin.logger)
    }

    fun stopCurrentMatch(session: GameSession) {
        stopRealtimeSystems()
        borderManager.reset(session)
        messageService.broadcastGameStopped()
        resetGame(session)
    }

    suspend fun finishMatch(session: GameSession, winner: Player?) {
        matchFlowService.moveToEnding(session)
        stopRealtimeSystems()
        borderManager.reset(session)

        if (winner != null && winner.isOnline) {
            victoryService.playVictory(winner)
            resetGame(session)
        } else {
            messageService.broadcastNoWinner()
            resetGame(session)
        }
    }

    private fun stopRealtimeSystems() {
        stopScoreboardTask()
        compassTrackingService.stop()
        borderManager.stop()
    }

    private fun resetGame(session: GameSession) {
        val completedScope = matchScope
        restoreMatchRules()
        clearPreparedSpawnLocations()
        playerSetupService.resetAllOnlinePlayersToLobby()
        scoreboardManager.clear()
        playerRegistry.clear()
        completedScope.resetRuntime()
        matchScopeHolder.current = matchScopeFactory.create()
    }

    private fun consumePreparedSpawnLocations(players: List<Player>, borderPlan: MatchBorderPlan): Map<Player, Location> {
        val playerIds = players.map { it.uniqueId }
        val cached = if (matchScope.preparedSpawnPlayerIds == playerIds) matchScope.preparedSpawnLocations else null
        clearPreparedSpawnLocations()
        return cached ?: borderManager.generateRandomSpawnLocations(players, borderPlan)
    }

    private suspend fun prepareMatchPlayersInBatches(
        spawnMap: Map<Player, Location>
    ) {
        val entries = spawnMap.entries.toList()
        if (entries.isEmpty()) {
            return
        }

        var fromIndex = 0
        while (fromIndex < entries.size) {
            val batch = entries.drop(fromIndex).take(MATCH_START_PLAYER_BATCH_SIZE)
            batch.forEach { (player, location) ->
                playerSetupService.prepareMatchPlayer(player, location)
            }

            fromIndex += batch.size
            if (fromIndex < entries.size) {
                plugin.nextTick()
            }
        }
    }

    private fun clearPreparedSpawnLocations() {
        matchScope.clearPreparedSpawns()
    }

    private fun consumePreparedBorderPlan(): MatchBorderPlan =
        matchScope.preparedBorderPlan ?: borderManager.createInitialBorderPlan()

    private fun applyMatchRules() {
        scoreboardManager.setNameTagsHidden(configManager.gameSettings.hideNameTags)
        val world = worldProvider.require()
        val originalLocatorBar = locatorBarRule()?.let { locatorBarRule ->
            world.getGameRuleValue(locatorBarRule).also {
                world.setGameRule(locatorBarRule, configManager.gameSettings.showPlayerLocatorBar)
            }
        }

        val originalAdvancementAnnouncements =
            if (!configManager.gameSettings.disableAdvancementAnnouncements) {
                null
            } else {
                showAdvancementMessagesRule()?.let { rule ->
                    world.getGameRuleValue(rule).also {
                        world.setGameRule(rule, false)
                    }
                }
            }

        matchScope.ruleSnapshot = MatchRuleSnapshot(
            locatorBar = originalLocatorBar,
            advancementAnnouncements = originalAdvancementAnnouncements
        )
    }

    private fun restoreMatchRules() {
        scoreboardManager.setNameTagsHidden(false)
        val snapshot = matchScope.ruleSnapshot
        snapshot?.locatorBar?.let {
            locatorBarRule()?.let { locatorBarRule ->
                worldProvider.require().setGameRule(locatorBarRule, it)
            }
        }

        snapshot?.advancementAnnouncements?.let {
            showAdvancementMessagesRule()?.let { rule ->
                worldProvider.require().setGameRule(rule, it)
            }
        }
        matchScope.ruleSnapshot = null
    }

    private fun startScoreboardTask(session: GameSession) {
        matchScope.scoreboardTask?.cancel()

        matchScope.scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!matchFlowService.canFinish(session)) return@Runnable

            session.aliveCount = playerRegistry.aliveCount()
            scoreboardManager.update(session)
        }, 0L, 20L)
    }

    private fun stopScoreboardTask() {
        matchScope.scoreboardTask?.cancel()
        matchScope.scoreboardTask = null
    }

    private fun locatorBarRule(): GameRule<Boolean>? =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            Class.forName("org.bukkit.GameRules")
                .getField("LOCATOR_BAR")
                .get(null) as GameRule<Boolean>
        }.getOrNull()

    private fun showAdvancementMessagesRule(): GameRule<Boolean>? =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            Class.forName("org.bukkit.GameRules")
                .getField("SHOW_ADVANCEMENT_MESSAGES")
                .get(null) as GameRule<Boolean>
        }.getOrNull()
}
