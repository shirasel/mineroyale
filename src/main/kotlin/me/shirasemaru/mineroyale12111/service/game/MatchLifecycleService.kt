package me.shirasemaru.mineroyale12111.service.game

import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.service.border.BorderManager
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import me.shirasemaru.mineroyale12111.service.player.PlayerSetupService
import me.shirasemaru.mineroyale12111.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.UUID

class MatchLifecycleService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val scoreboardManager: ScoreboardManager,
    private val playerRegistry: PlayerRegistry,
    private val playerSetupService: PlayerSetupService,
    private val borderManager: BorderManager,
    private val compassTrackingService: CompassTrackingService,
    private val victoryService: VictoryService,
    private val messageService: MessageService,
    private val matchFlowService: MatchFlowService
) {

    private companion object {
        const val MATCH_START_PLAYER_BATCH_SIZE = 4
    }

    private var scoreboardTask: BukkitTask? = null
    private var originalAnnounceAdvancements: Boolean? = null
    private var originalLocatorBar: Boolean? = null
    private var preparedSpawnLocations: Map<Player, Location>? = null
    private var preparedSpawnPlayerIds: List<UUID>? = null

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
        if (preparedSpawnPlayerIds == playerIds && preparedSpawnLocations?.size == players.size) {
            return
        }

        preparedSpawnLocations = borderManager.generateRandomSpawnLocations(players)
        preparedSpawnPlayerIds = playerIds
    }

    fun discardPreparedSpawnLocations() {
        clearPreparedSpawnLocations()
    }

    fun startMatch(
        session: GameSession,
        players: List<Player>,
        onPlayersReady: () -> Unit = {},
        onMatchComplete: () -> Unit
    ) {
        matchFlowService.moveToRunning(session)
        playerRegistry.resetForMatch(players)
        session.participantCount = players.size
        session.aliveCount = players.size

        applyMatchRules()
        val spawnMap = consumePreparedSpawnLocations(players)

        preloadSpawnChunks(spawnMap) {
            runStartStep {
                borderManager.initialize(session)
                prepareMatchPlayersInBatches(spawnMap) {
                    runStartStep {
                        onPlayersReady()
                        borderManager.runPhases(session, onMatchComplete)
                        startScoreboardTask(session)
                        compassTrackingService.start(playerRegistry::getAlivePlayers)
                        messageService.logMatchStarted(plugin.logger)
                    }
                }
            }
        }
    }

    fun stopCurrentMatch(session: GameSession) {
        stopRealtimeSystems()
        borderManager.reset(session)
        messageService.broadcastGameStopped()
        resetGame(session)
    }

    fun finishMatch(session: GameSession, winner: Player?, onResetCompleted: (() -> Unit)? = null) {
        matchFlowService.moveToEnding(session)
        stopRealtimeSystems()
        borderManager.reset(session)

        if (winner != null && winner.isOnline) {
            victoryService.playVictory(winner) {
                resetGame(session)
                onResetCompleted?.invoke()
            }
        } else {
            messageService.broadcastNoWinner()
            resetGame(session)
            onResetCompleted?.invoke()
        }
    }

    private fun stopRealtimeSystems() {
        stopScoreboardTask()
        compassTrackingService.stop()
        borderManager.stop()
    }

    private fun resetGame(session: GameSession) {
        restoreMatchRules()
        clearPreparedSpawnLocations()
        playerSetupService.resetAllOnlinePlayersToLobby()
        scoreboardManager.clear()
        playerRegistry.clear()
        session.resetToWaiting()
    }

    private fun consumePreparedSpawnLocations(players: List<Player>): Map<Player, Location> {
        val playerIds = players.map { it.uniqueId }
        val cached = if (preparedSpawnPlayerIds == playerIds) preparedSpawnLocations else null
        clearPreparedSpawnLocations()
        return cached ?: borderManager.generateRandomSpawnLocations(players)
    }

    private fun preloadSpawnChunks(
        spawnMap: Map<Player, Location>,
        onLoaded: () -> Unit
    ) {
        val futures = spawnMap.values
            .mapNotNull { location -> location.world?.getChunkAtAsync(location, true) }
            .distinct()

        if (futures.isEmpty()) {
            onLoaded()
            return
        }

        if (futures.all { it.isDone }) {
            onLoaded()
            return
        }

        CompletableFuture.allOf(*futures.toTypedArray())
            .thenRun(onLoaded)
    }

    private fun prepareMatchPlayersInBatches(
        spawnMap: Map<Player, Location>,
        onFinished: () -> Unit
    ) {
        val entries = spawnMap.entries.toList()
        if (entries.isEmpty()) {
            onFinished()
            return
        }

        fun processBatch(fromIndex: Int) {
            val batch = entries.drop(fromIndex).take(MATCH_START_PLAYER_BATCH_SIZE)
            batch.forEach { (player, location) ->
                playerSetupService.prepareMatchPlayer(player, location)
            }

            val nextIndex = fromIndex + batch.size
            if (nextIndex >= entries.size) {
                onFinished()
                return
            }

            runStartStep {
                processBatch(nextIndex)
            }
        }

        processBatch(0)
    }

    private fun runStartStep(action: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable(action))
    }

    private fun clearPreparedSpawnLocations() {
        preparedSpawnLocations = null
        preparedSpawnPlayerIds = null
    }

    private fun applyMatchRules() {
        scoreboardManager.setNameTagsHidden(configManager.gameSettings.hideNameTags)
        val world = configManager.gameWorld
        locatorBarRule()?.let { locatorBarRule ->
            originalLocatorBar = world.getGameRuleValue(locatorBarRule)
            world.setGameRule(locatorBarRule, configManager.gameSettings.showPlayerLocatorBar)
        }

        if (!configManager.gameSettings.disableAdvancementAnnouncements) {
            originalAnnounceAdvancements = null
            return
        }

        showAdvancementMessagesRule()?.let { rule ->
            originalAnnounceAdvancements = world.getGameRuleValue(rule)
            world.setGameRule(rule, false)
        }
    }

    private fun restoreMatchRules() {
        scoreboardManager.setNameTagsHidden(false)
        originalLocatorBar?.let {
            locatorBarRule()?.let { locatorBarRule ->
                configManager.gameWorld.setGameRule(locatorBarRule, it)
            }
            originalLocatorBar = null
        }

        originalAnnounceAdvancements?.let {
            showAdvancementMessagesRule()?.let { rule ->
                configManager.gameWorld.setGameRule(rule, it)
            }
            originalAnnounceAdvancements = null
        }
    }

    private fun startScoreboardTask(session: GameSession) {
        scoreboardTask?.cancel()

        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!matchFlowService.canFinish(session)) return@Runnable

            session.aliveCount = playerRegistry.aliveCount()
            scoreboardManager.update(session)
        }, 0L, 20L)
    }

    private fun stopScoreboardTask() {
        scoreboardTask?.cancel()
        scoreboardTask = null
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
