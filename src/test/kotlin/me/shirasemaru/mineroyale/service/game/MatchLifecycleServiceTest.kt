package me.shirasemaru.mineroyale.service.game

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.shirasemaru.mineroyale.bootstrap.GameWorldProvider
import me.shirasemaru.mineroyale.bootstrap.OnlinePlayerProvider
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.GameSettings
import me.shirasemaru.mineroyale.game.GameSession
import me.shirasemaru.mineroyale.game.GameState
import me.shirasemaru.mineroyale.game.MatchScopeFactory
import me.shirasemaru.mineroyale.game.MatchScopeHolder
import me.shirasemaru.mineroyale.service.border.BorderManager
import me.shirasemaru.mineroyale.service.border.MatchBorderPlan
import me.shirasemaru.mineroyale.service.player.PlayerRegistry
import me.shirasemaru.mineroyale.service.player.PlayerSetupService
import me.shirasemaru.mineroyale.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale.ui.ScoreboardManager
import org.bukkit.Chunk
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchLifecycleServiceTest {

    @Test
    fun `getEligiblePlayers filters out spectators and sorts by name`() {
        val activeB = mockPlayer("bravo", GameMode.SURVIVAL)
        val spectator = mockPlayer("charlie", GameMode.ADVENTURE)
        val activeA = mockPlayer("alpha", GameMode.ADVENTURE)
        val playerRegistry = mockk<PlayerRegistry>()

        every { playerRegistry.isSpectator(spectator) } returns true
        every { playerRegistry.isSpectator(activeA) } returns false
        every { playerRegistry.isSpectator(activeB) } returns false

        val service = createService(
            playerRegistry = playerRegistry,
            onlinePlayers = linkedSetOf(activeB, spectator, activeA)
        )

        val result = service.getEligiblePlayers()

        assertEquals(listOf(activeA, activeB), result)
    }

    @Test
    fun `startMatch moves session to running and initializes collaborators`() {
        val borderManager = mockk<BorderManager>()
        val playerRegistry = mockk<PlayerRegistry>()
        val playerSetupService = mockk<PlayerSetupService>()
        val compassTrackingService = mockk<CompassTrackingService>()
        val victoryService = mockk<VictoryService>()
        val deathMarkerService = mockk<DeathMarkerService>()
        val messageService = mockk<MessageService>()
        val matchFlowService = mockk<MatchFlowService>()
        val plugin = mockPlugin()
        val scoreboardManager = mockk<ScoreboardManager>()
        val configManager = mockConfigManager()
        val worldProvider = mockWorldProvider()
        val matchScopeFactory = MatchScopeFactory()
        val matchScopeHolder = MatchScopeHolder(matchScopeFactory.create())
        val borderPlan = MatchBorderPlan(centerX = 12.0, centerZ = -8.0, size = 100.0)

        every { matchFlowService.moveToRunning(any()) } answers { firstArg<GameSession>().apply { state = GameState.RUNNING; pvpEnabled = false } }
        every { playerRegistry.resetForMatch(any()) } just runs
        every { borderManager.initialize(any(), borderPlan) } just runs
        every { borderManager.createInitialBorderPlan() } returns borderPlan
        every { borderManager.generateRandomSpawnLocations(any(), borderPlan) } returns emptyMap()
        every { playerSetupService.prepareMatchPlayer(any(), any()) } just runs
        every { borderManager.runPhases(any(), any()) } just runs
        every { compassTrackingService.start(any()) } just runs
        every { deathMarkerService.clearMarkers() } just runs
        every { messageService.logMatchStarted(any()) } just runs
        every { scoreboardManager.setNameTagsHidden(any()) } just runs

        val service = MatchLifecycleService(
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
            matchScopeHolder = matchScopeHolder,
            onlinePlayerProvider = StaticOnlinePlayerProvider(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val session = GameSession()
        val players = listOf(mockPlayer("alpha"), mockPlayer("bravo"))

        runBlocking {
            service.startMatch(session, players, onPlayersReady = {}) {}
        }

        assertEquals(GameState.RUNNING, session.state)
        assertEquals(2, session.participantCount)
        assertEquals(2, session.aliveCount)
        verify { borderManager.initialize(session, borderPlan) }
        verify { deathMarkerService.clearMarkers() }
        verify(exactly = 0) { playerSetupService.prepareMatchPlayer(any(), any()) }
        verify { borderManager.runPhases(session, any()) }
        verify { compassTrackingService.start(any()) }
        verify { messageService.logMatchStarted(any()) }
    }

    @Test
    fun `prepared spawn locations reuse the same border plan at match start`() {
        val borderManager = mockk<BorderManager>()
        val playerRegistry = mockk<PlayerRegistry>()
        val playerSetupService = mockk<PlayerSetupService>()
        val compassTrackingService = mockk<CompassTrackingService>()
        val victoryService = mockk<VictoryService>()
        val deathMarkerService = mockk<DeathMarkerService>()
        val messageService = mockk<MessageService>()
        val matchFlowService = mockk<MatchFlowService>()
        val plugin = mockPlugin()
        val scoreboardManager = mockk<ScoreboardManager>()
        val configManager = mockConfigManager()
        val worldProvider = mockWorldProvider()
        val matchScopeFactory = MatchScopeFactory()
        val matchScopeHolder = MatchScopeHolder(matchScopeFactory.create())
        val borderPlan = MatchBorderPlan(centerX = 20.0, centerZ = 40.0, size = 120.0)
        val player = mockPlayer("alpha")
        val spawnWorld = mockk<World>()
        val chunk = mockk<Chunk>()
        val spawn = Location(spawnWorld, 18.0, 70.0, 38.0)

        every { matchFlowService.moveToRunning(any()) } answers { firstArg<GameSession>().apply { state = GameState.RUNNING } }
        every { playerRegistry.resetForMatch(any()) } just runs
        every { borderManager.createInitialBorderPlan() } returns borderPlan
        every { borderManager.generateRandomSpawnLocations(listOf(player), borderPlan) } returns mapOf(player to spawn)
        every { borderManager.initialize(any(), borderPlan) } just runs
        every { playerSetupService.prepareMatchPlayer(player, spawn) } just runs
        every { borderManager.runPhases(any(), any()) } just runs
        every { compassTrackingService.start(any()) } just runs
        every { deathMarkerService.clearMarkers() } just runs
        every { messageService.logMatchStarted(any()) } just runs
        every { scoreboardManager.setNameTagsHidden(any()) } just runs
        every { spawnWorld.getChunkAtAsync(spawn, true) } returns CompletableFuture.completedFuture(chunk)

        val service = MatchLifecycleService(
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
            matchScopeHolder = matchScopeHolder,
            onlinePlayerProvider = StaticOnlinePlayerProvider(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        service.prepareSpawnLocations(listOf(player))
        runBlocking {
            service.startMatch(GameSession(), listOf(player), onPlayersReady = {}) {}
        }

        verify(exactly = 1) { borderManager.createInitialBorderPlan() }
        verify(exactly = 1) { borderManager.generateRandomSpawnLocations(listOf(player), borderPlan) }
        verify(exactly = 1) { borderManager.initialize(any(), borderPlan) }
        verify(exactly = 1) { deathMarkerService.clearMarkers() }
        verify(exactly = 1) { playerSetupService.prepareMatchPlayer(player, spawn) }
    }

    @Test
    fun `stopCurrentMatch resets session and clears collaborators`() {
        val borderManager = mockk<BorderManager>()
        val playerRegistry = mockk<PlayerRegistry>()
        val playerSetupService = mockk<PlayerSetupService>()
        val compassTrackingService = mockk<CompassTrackingService>()
        val victoryService = mockk<VictoryService>()
        val deathMarkerService = mockk<DeathMarkerService>(relaxed = true)
        val messageService = mockk<MessageService>()
        val matchFlowService = mockk<MatchFlowService>()
        val plugin = mockPlugin()
        val scoreboardManager = mockk<ScoreboardManager>()
        val configManager = mockConfigManager()
        val worldProvider = mockWorldProvider()
        val matchScopeFactory = MatchScopeFactory()
        val matchScopeHolder = MatchScopeHolder(matchScopeFactory.create())

        every { borderManager.stop() } just runs
        every { borderManager.reset(any()) } just runs
        every { compassTrackingService.stop() } just runs
        every { messageService.broadcastGameStopped() } just runs
        every { playerSetupService.resetAllOnlinePlayersToLobby() } just runs
        every { scoreboardManager.clear() } just runs
        every { scoreboardManager.setNameTagsHidden(any()) } just runs
        every { playerRegistry.clear() } just runs

        val service = MatchLifecycleService(
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
            matchScopeHolder = matchScopeHolder,
            onlinePlayerProvider = StaticOnlinePlayerProvider(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val session = matchScopeHolder.current.session.apply {
            state = GameState.RUNNING
            participantCount = 4
            aliveCount = 2
            currentPhase = 2
        }

        service.stopCurrentMatch(session)

        assertEquals(GameState.WAITING, session.state)
        assertEquals(0, session.participantCount)
        assertEquals(0, session.aliveCount)
        verify { borderManager.stop() }
        verify { borderManager.reset(session) }
        verify { compassTrackingService.stop() }
        verify { messageService.broadcastGameStopped() }
        verify { scoreboardManager.clear() }
        verify { playerRegistry.clear() }
    }

    @Test
    fun `finishMatch with winner runs victory flow and resets session after callback`() {
        val borderManager = mockk<BorderManager>()
        val playerRegistry = mockk<PlayerRegistry>()
        val playerSetupService = mockk<PlayerSetupService>()
        val compassTrackingService = mockk<CompassTrackingService>()
        val victoryService = mockk<VictoryService>()
        val deathMarkerService = mockk<DeathMarkerService>(relaxed = true)
        val messageService = mockk<MessageService>()
        val matchFlowService = mockk<MatchFlowService>()
        val plugin = mockPlugin()
        val scoreboardManager = mockk<ScoreboardManager>()
        val configManager = mockConfigManager()
        val worldProvider = mockWorldProvider()
        val matchScopeFactory = MatchScopeFactory()
        val matchScopeHolder = MatchScopeHolder(matchScopeFactory.create())
        val winner = mockPlayer("winner")

        every { matchFlowService.moveToEnding(any()) } answers { firstArg<GameSession>().state = GameState.ENDING }
        every { borderManager.stop() } just runs
        every { borderManager.reset(any()) } just runs
        every { compassTrackingService.stop() } just runs
        every { playerSetupService.resetAllOnlinePlayersToLobby() } just runs
        every { scoreboardManager.clear() } just runs
        every { scoreboardManager.setNameTagsHidden(any()) } just runs
        every { playerRegistry.clear() } just runs
        coEvery { victoryService.playVictory(winner) } just runs

        val service = MatchLifecycleService(
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
            matchScopeHolder = matchScopeHolder,
            onlinePlayerProvider = StaticOnlinePlayerProvider(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val session = matchScopeHolder.current.session.apply {
            state = GameState.RUNNING
            aliveCount = 1
        }

        runBlocking {
            service.finishMatch(session, winner)
        }

        assertEquals(GameState.WAITING, session.state)
        coVerify { victoryService.playVictory(winner) }
        verify(exactly = 0) { messageService.broadcastNoWinner() }
        verify { scoreboardManager.clear() }
        verify { playerRegistry.clear() }
    }

    private fun createService(
        playerRegistry: PlayerRegistry = mockk(),
        onlinePlayers: Collection<Player> = emptyList()
    ): MatchLifecycleService =
        MatchLifecycleService(
            plugin = mockPlugin(),
            configManager = mockConfigManager(),
            worldProvider = mockWorldProvider(),
            scoreboardManager = mockk(),
            playerRegistry = playerRegistry,
            playerSetupService = mockk(),
            borderManager = mockk(),
            compassTrackingService = mockk(),
            victoryService = mockk(),
            deathMarkerService = mockk(relaxed = true),
            messageService = mockk(),
            matchFlowService = mockk(),
            matchScopeFactory = MatchScopeFactory(),
            matchScopeHolder = MatchScopeHolder(MatchScopeFactory().create()),
            onlinePlayerProvider = StaticOnlinePlayerProvider(onlinePlayers),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

    private fun mockConfigManager(): ConfigManager {
        val world = mockk<World>()
        val gameSettings = mockk<GameSettings>()
        val configManager = mockk<ConfigManager>()
        every { configManager.gameSettings } returns gameSettings
        every { gameSettings.hideNameTags } returns false
        every { gameSettings.showPlayerLocatorBar } returns true
        every { gameSettings.disableAdvancementAnnouncements } returns false
        return configManager
    }

    private fun mockWorldProvider(): GameWorldProvider {
        val world = mockk<World>(relaxed = true)
        return mockk {
            every { require() } returns world
            every { find() } returns world
        }
    }

    private fun mockPlugin(): JavaPlugin {
        val server = mockk<Server>()
        val scheduler = mockk<BukkitScheduler>()
        val plugin = mockk<JavaPlugin>()
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { plugin.logger } returns Logger.getLogger("test")
        every { scheduler.runTask(any<JavaPlugin>(), any<Runnable>()) } answers {
            (invocation.args[1] as Runnable).run()
            mockk(relaxed = true)
        }
        every { scheduler.runTaskLater(any<JavaPlugin>(), any<Runnable>(), any<Long>()) } returns mockk(relaxed = true)
        return plugin
    }

    private fun mockPlayer(name: String, gameMode: GameMode = GameMode.SURVIVAL): Player {
        val player = mockk<Player>()
        every { player.name } returns name
        every { player.uniqueId } returns UUID.nameUUIDFromBytes(name.toByteArray())
        every { player.gameMode } returns gameMode
        every { player.isOnline } returns true
        return player
    }

    private class StaticOnlinePlayerProvider(
        override val onlinePlayers: Collection<Player> = emptyList()
    ) : OnlinePlayerProvider
}
