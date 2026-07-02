package me.shirasemaru.mineroyale.game

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.shirasemaru.mineroyale.Mineroyale
import me.shirasemaru.mineroyale.bootstrap.GameWorldProvider
import me.shirasemaru.mineroyale.bootstrap.OnlinePlayerProvider
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.service.border.BorderManager
import me.shirasemaru.mineroyale.service.game.CountdownService
import me.shirasemaru.mineroyale.service.game.DeathMarkerService
import me.shirasemaru.mineroyale.service.game.MatchFlowService
import me.shirasemaru.mineroyale.service.game.MatchLifecycleService
import me.shirasemaru.mineroyale.service.game.MessageService
import me.shirasemaru.mineroyale.service.game.VictoryService
import me.shirasemaru.mineroyale.service.item.EndCrystalService
import me.shirasemaru.mineroyale.service.player.PlayerRegistry
import me.shirasemaru.mineroyale.service.player.PlayerSetupService
import me.shirasemaru.mineroyale.service.player.SpectatorService
import me.shirasemaru.mineroyale.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale.ui.ScoreboardManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.inventory.PlayerInventory
import org.bukkit.scheduler.BukkitScheduler
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GameManagerDeathFlowTest {

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `handlePlayerDeath eliminates player and keeps match running while multiple players remain`() {
        mockkStatic(Bukkit::class)

        val fixture = createFixture()
        val eliminated = mockPlayer("eliminated")
        val survivorA = mockPlayer("survivor-a")
        val survivorB = mockPlayer("survivor-b")
        stubPlayers(eliminated, survivorA, survivorB)
        prepareRunningMatch(
            gameManager = fixture.gameManager,
            playerRegistry = fixture.playerRegistry,
            players = listOf(eliminated, survivorA, survivorB)
        )
        val deathLocation = Location(mockk<World>(), 15.0, 64.0, -3.0)

        fixture.gameManager.handlePlayerDeath(eliminated, deathLocation)

        assertEquals(GameState.RUNNING, fixture.gameManager.getState())
        assertFalse(fixture.playerRegistry.isAlive(eliminated))
        assertEquals(2, sessionOf(fixture.gameManager).aliveCount)
        verify(exactly = 1) { fixture.spectatorService.applySpectatorMode(eliminated) }
        verify(exactly = 1) { fixture.deathMarkerService.spawnMarker(eliminated, deathLocation) }
        verify(exactly = 1) { fixture.messageService.broadcastPlayerEliminated("eliminated", 2) }
        coVerify(exactly = 0) { fixture.victoryService.playVictory(any()) }
    }

    @Test
    fun `handlePlayerDeath finishes match and resets state when winner is decided`() {
        mockkStatic(Bukkit::class)

        val fixture = createFixture()
        val eliminated = mockPlayer("eliminated")
        val winner = mockPlayer("winner")
        stubPlayers(eliminated, winner)
        prepareRunningMatch(
            gameManager = fixture.gameManager,
            playerRegistry = fixture.playerRegistry,
            players = listOf(eliminated, winner)
        )
        val deathLocation = Location(mockk<World>(), 2.0, 70.0, 8.0)

        fixture.gameManager.handlePlayerDeath(eliminated, deathLocation)

        assertEquals(GameState.WAITING, fixture.gameManager.getState())
        assertFalse(fixture.gameManager.isRunning())
        assertEquals(29999984.0, fixture.border.size, 0.001)
        assertEquals(0.0, fixture.border.center.x, 0.001)
        assertEquals(0.0, fixture.border.center.z, 0.001)
        assertEquals(0, fixture.playerRegistry.aliveCount())
        verify(exactly = 1) { fixture.spectatorService.applySpectatorMode(eliminated) }
        verify(exactly = 1) { fixture.deathMarkerService.spawnMarker(eliminated, deathLocation) }
        verify(exactly = 1) { fixture.messageService.broadcastPlayerEliminated("eliminated", 1) }
        coVerify(exactly = 1) { fixture.victoryService.playVictory(winner) }
        verify(exactly = 1) { fixture.playerSetupService.resetAllOnlinePlayersToLobby() }
        verify(exactly = 1) { fixture.scoreboardManager.clear() }
        verify(exactly = 1) { fixture.compassTrackingService.stop() }
    }

    private fun createFixture(): Fixture {
        val plugin = mockk<Mineroyale>()
        val server = mockk<Server>()
        val scheduler = mockk<BukkitScheduler>(relaxed = true)
        val border = mockBorder()
        val world = mockk<World>()
        val configManager = mockk<ConfigManager>()
        val worldProvider = mockk<GameWorldProvider>()
        val playerRegistry = PlayerRegistry()
        val playerSetupService = mockk<PlayerSetupService>()
        val spectatorService = mockk<SpectatorService>()
        val countdownService = mockk<CountdownService>()
        val matchFlowService = MatchFlowService()
        val matchScopeFactory = MatchScopeFactory()
        val matchScopeHolder = MatchScopeHolder(matchScopeFactory.create())
        val coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        val messageService = mockk<MessageService>()
        val scoreboardManager = mockk<ScoreboardManager>()
        val victoryService = mockk<VictoryService>()
        val compassTrackingService = mockk<CompassTrackingService>()
        val endCrystalService = mockk<EndCrystalService>(relaxed = true)
        val deathMarkerService = mockk<DeathMarkerService>()

        every { plugin.namespace() } returns "mineroyale"
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { worldProvider.require() } returns world
        every { worldProvider.find() } returns world
        every { world.worldBorder } returns border
        every { countdownService.cancel(any()) } just runs
        every { playerSetupService.resetAllOnlinePlayersToLobby() } just runs
        every { spectatorService.applySpectatorMode(any()) } just runs
        every { spectatorService.refreshTargets(any(), any(), any()) } just runs
        every { messageService.spectatorHeadDisplayName(any()) } answers { Component.text(firstArg<String>()) }
        every { messageService.broadcastPlayerEliminated(any(), any()) } just runs
        every { scoreboardManager.clear() } just runs
        every { scoreboardManager.setNameTagsHidden(any()) } just runs
        every { compassTrackingService.stop() } just runs
        every { deathMarkerService.spawnMarker(any(), any()) } just runs
        coEvery { victoryService.playVictory(any()) } just runs

        val borderManager = BorderManager(
            plugin = plugin,
            configManager = configManager,
            worldProvider = worldProvider,
            messageService = messageService,
            onPvpStateChanged = { },
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
            matchScopeHolder = matchScopeHolder,
            onlinePlayerProvider = EmptyOnlinePlayerProvider,
            coroutineScope = coroutineScope
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

        return Fixture(
            gameManager = gameManager,
            playerRegistry = playerRegistry,
            playerSetupService = playerSetupService,
            spectatorService = spectatorService,
            messageService = messageService,
            scoreboardManager = scoreboardManager,
            victoryService = victoryService,
            compassTrackingService = compassTrackingService,
            deathMarkerService = deathMarkerService,
            border = border
        )
    }

    private fun prepareRunningMatch(
        gameManager: GameManager,
        playerRegistry: PlayerRegistry,
        players: List<Player>
    ) {
        playerRegistry.resetForMatch(players)
        sessionOf(gameManager).apply {
            state = GameState.RUNNING
            participantCount = players.size
            aliveCount = players.size
            pvpEnabled = true
        }
    }

    private fun sessionOf(gameManager: GameManager): GameSession {
        val holderField = GameManager::class.java.getDeclaredField("matchScopeHolder")
        holderField.isAccessible = true
        val holder = holderField.get(gameManager) as MatchScopeHolder
        return holder.current.session
    }

    private fun stubPlayers(vararg players: Player) {
        val byId = players.associateBy { it.uniqueId }
        every { Bukkit.getPlayer(any<UUID>()) } answers { byId[firstArg()] }
    }

    private fun mockPlayer(name: String): Player {
        val inventory = mockk<PlayerInventory>(relaxed = true)
        val player = mockk<Player>()
        every { player.name } returns name
        every { player.uniqueId } returns UUID.nameUUIDFromBytes(name.toByteArray())
        every { player.isOnline } returns true
        every { player.inventory } returns inventory
        every { player.location } returns Location(null, 0.0, 64.0, 0.0)
        return player
    }

    private fun mockBorder(): WorldBorder {
        var size = 100.0
        var centerX = 10.0
        var centerZ = -10.0

        val border = mockk<WorldBorder>()
        every { border.size } answers { size }
        every { border.size = any() } answers { size = firstArg() }
        every { border.center } answers { Location(null, centerX, 0.0, centerZ) }
        every { border.setCenter(any<Double>(), any<Double>()) } answers {
            centerX = firstArg()
            centerZ = secondArg()
        }
        return border
    }

    private data class Fixture(
        val gameManager: GameManager,
        val playerRegistry: PlayerRegistry,
        val playerSetupService: PlayerSetupService,
        val spectatorService: SpectatorService,
        val messageService: MessageService,
        val scoreboardManager: ScoreboardManager,
        val victoryService: VictoryService,
        val compassTrackingService: CompassTrackingService,
        val deathMarkerService: DeathMarkerService,
        val border: WorldBorder
    )

    private object EmptyOnlinePlayerProvider : OnlinePlayerProvider {
        override val onlinePlayers: Collection<Player> = emptyList()
    }
}
