package me.shirasemaru.mineroyale12111.game

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import me.shirasemaru.mineroyale12111.Mineroyale12111
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.service.game.CountdownService
import me.shirasemaru.mineroyale12111.service.game.DeathMarkerService
import me.shirasemaru.mineroyale12111.service.game.MessageService
import me.shirasemaru.mineroyale12111.service.game.VictoryService
import me.shirasemaru.mineroyale12111.service.item.EndCrystalService
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import me.shirasemaru.mineroyale12111.service.player.PlayerSetupService
import me.shirasemaru.mineroyale12111.service.player.SpectatorService
import me.shirasemaru.mineroyale12111.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.inventory.PlayerInventory
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
        verify(exactly = 0) { fixture.victoryService.playVictory(any(), any()) }
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
        verify(exactly = 1) { fixture.victoryService.playVictory(winner, any()) }
        verify(exactly = 1) { fixture.playerSetupService.resetAllOnlinePlayersToLobby() }
        verify(exactly = 1) { fixture.scoreboardManager.clear() }
        verify(exactly = 1) { fixture.compassTrackingService.stop() }
    }

    private fun createFixture(): Fixture {
        val plugin = mockk<Mineroyale12111>()
        val border = mockBorder()
        val world = mockk<World>()
        val configManager = mockk<ConfigManager>()
        val playerRegistry = PlayerRegistry()
        val playerSetupService = mockk<PlayerSetupService>()
        val spectatorService = mockk<SpectatorService>()
        val countdownService = mockk<CountdownService>()
        val messageService = mockk<MessageService>()
        val scoreboardManager = mockk<ScoreboardManager>()
        val victoryService = mockk<VictoryService>()
        val compassTrackingService = mockk<CompassTrackingService>()
        val endCrystalService = mockk<EndCrystalService>(relaxed = true)
        val deathMarkerService = mockk<DeathMarkerService>()

        every { plugin.namespace() } returns "mineroyale12111"
        every { configManager.gameWorld } returns world
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
        every { victoryService.playVictory(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
        }

        val gameManager = GameManager(
            plugin = plugin,
            configManager = configManager,
            playerRegistry = playerRegistry,
            playerSetupService = playerSetupService,
            spectatorService = spectatorService,
            countdownService = countdownService,
            messageService = messageService,
            scoreboardManager = scoreboardManager,
            victoryService = victoryService,
            compassTrackingService = compassTrackingService,
            endCrystalService = endCrystalService,
            deathMarkerService = deathMarkerService
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
        val field = GameManager::class.java.getDeclaredField("session")
        field.isAccessible = true
        return field.get(gameManager) as GameSession
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
}
