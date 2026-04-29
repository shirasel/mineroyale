package me.shirasemaru.mineroyale12111.service.game

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.config.GameSettings
import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.game.GameState
import me.shirasemaru.mineroyale12111.service.border.BorderManager
import me.shirasemaru.mineroyale12111.service.border.MatchBorderPlan
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import me.shirasemaru.mineroyale12111.service.player.PlayerSetupService
import me.shirasemaru.mineroyale12111.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchLifecycleServiceTest {

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `getEligiblePlayers filters out spectators and sorts by name`() {
        mockkStatic(Bukkit::class)
        val activeB = mockPlayer("bravo", GameMode.SURVIVAL)
        val spectator = mockPlayer("charlie", GameMode.ADVENTURE)
        val activeA = mockPlayer("alpha", GameMode.ADVENTURE)
        val playerRegistry = mockk<PlayerRegistry>()

        every { Bukkit.getOnlinePlayers() } returns linkedSetOf(activeB, spectator, activeA)
        every { playerRegistry.isSpectator(spectator) } returns true
        every { playerRegistry.isSpectator(activeA) } returns false
        every { playerRegistry.isSpectator(activeB) } returns false

        val service = createService(playerRegistry)

        val result = service.getEligiblePlayers()

        assertEquals(listOf(activeA, activeB), result)
    }

    @Test
    fun `startMatch moves session to running and initializes collaborators`() {
        mockkStatic(Bukkit::class)
        val scheduler = mockk<BukkitScheduler>()
        val task = mockk<BukkitTask>()
        every { Bukkit.getScheduler() } returns scheduler
        every { scheduler.runTaskTimer(any<JavaPlugin>(), any<Runnable>(), 0L, 20L) } returns task
        every { scheduler.runTask(any<JavaPlugin>(), any<Runnable>()) } answers {
            (invocation.args[1] as Runnable).run()
            mockk(relaxed = true)
        }

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
            scoreboardManager = scoreboardManager,
            playerRegistry = playerRegistry,
            playerSetupService = playerSetupService,
            borderManager = borderManager,
            compassTrackingService = compassTrackingService,
            victoryService = victoryService,
            deathMarkerService = deathMarkerService,
            messageService = messageService,
            matchFlowService = matchFlowService
        )

        val session = GameSession()
        val players = listOf(mockPlayer("alpha"), mockPlayer("bravo"))

        service.startMatch(session, players, onPlayersReady = {}) {}

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
        mockkStatic(Bukkit::class)
        val scheduler = mockk<BukkitScheduler>()
        val task = mockk<BukkitTask>()
        every { Bukkit.getScheduler() } returns scheduler
        every { scheduler.runTaskTimer(any<JavaPlugin>(), any<Runnable>(), 0L, 20L) } returns task
        every { scheduler.runTask(any<JavaPlugin>(), any<Runnable>()) } answers {
            (invocation.args[1] as Runnable).run()
            mockk(relaxed = true)
        }

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
        every { spawnWorld.getChunkAtAsync(spawn, true) } returns java.util.concurrent.CompletableFuture.completedFuture(chunk)

        val service = MatchLifecycleService(
            plugin = plugin,
            configManager = configManager,
            scoreboardManager = scoreboardManager,
            playerRegistry = playerRegistry,
            playerSetupService = playerSetupService,
            borderManager = borderManager,
            compassTrackingService = compassTrackingService,
            victoryService = victoryService,
            deathMarkerService = deathMarkerService,
            messageService = messageService,
            matchFlowService = matchFlowService
        )

        service.prepareSpawnLocations(listOf(player))
        service.startMatch(GameSession(), listOf(player), onPlayersReady = {}) {}

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
            scoreboardManager = scoreboardManager,
            playerRegistry = playerRegistry,
            playerSetupService = playerSetupService,
            borderManager = borderManager,
            compassTrackingService = compassTrackingService,
            victoryService = victoryService,
            deathMarkerService = deathMarkerService,
            messageService = messageService,
            matchFlowService = matchFlowService
        )

        val session = GameSession(
            state = GameState.RUNNING,
            participantCount = 4,
            aliveCount = 2,
            currentPhase = 2
        )

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
        val winner = mockPlayer("winner")

        every { matchFlowService.moveToEnding(any()) } answers { firstArg<GameSession>().state = GameState.ENDING }
        every { borderManager.stop() } just runs
        every { borderManager.reset(any()) } just runs
        every { compassTrackingService.stop() } just runs
        every { playerSetupService.resetAllOnlinePlayersToLobby() } just runs
        every { scoreboardManager.clear() } just runs
        every { scoreboardManager.setNameTagsHidden(any()) } just runs
        every { playerRegistry.clear() } just runs
        every { victoryService.playVictory(winner, any()) } answers {
            secondArg<() -> Unit>().invoke()
        }

        val service = MatchLifecycleService(
            plugin = plugin,
            configManager = configManager,
            scoreboardManager = scoreboardManager,
            playerRegistry = playerRegistry,
            playerSetupService = playerSetupService,
            borderManager = borderManager,
            compassTrackingService = compassTrackingService,
            victoryService = victoryService,
            deathMarkerService = deathMarkerService,
            messageService = messageService,
            matchFlowService = matchFlowService
        )

        val session = GameSession(state = GameState.RUNNING, aliveCount = 1)

        service.finishMatch(session, winner)

        assertEquals(GameState.WAITING, session.state)
        verify { victoryService.playVictory(winner, any()) }
        verify(exactly = 0) { messageService.broadcastNoWinner() }
        verify { scoreboardManager.clear() }
        verify { playerRegistry.clear() }
    }

    private fun createService(playerRegistry: PlayerRegistry = mockk()): MatchLifecycleService =
        MatchLifecycleService(
            plugin = mockPlugin(),
            configManager = mockConfigManager(),
            scoreboardManager = mockk(),
            playerRegistry = playerRegistry,
            playerSetupService = mockk(),
            borderManager = mockk(),
            compassTrackingService = mockk(),
            victoryService = mockk(),
            deathMarkerService = mockk(relaxed = true),
            messageService = mockk(),
            matchFlowService = mockk()
        )

    private fun mockConfigManager(): ConfigManager {
        val world = mockk<World>()
        val gameSettings = mockk<GameSettings>()
        val configManager = mockk<ConfigManager>()
        every { configManager.gameWorld } returns world
        every { configManager.gameSettings } returns gameSettings
        every { gameSettings.hideNameTags } returns false
        every { gameSettings.showPlayerLocatorBar } returns true
        every { gameSettings.disableAdvancementAnnouncements } returns false
        return configManager
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
        return plugin
    }

    private fun mockPlayer(name: String, gameMode: GameMode = GameMode.SURVIVAL): Player {
        val player = mockk<Player>()
        every { player.name } returns name
        every { player.uniqueId } returns java.util.UUID.nameUUIDFromBytes(name.toByteArray())
        every { player.gameMode } returns gameMode
        every { player.isOnline } returns true
        return player
    }
}
