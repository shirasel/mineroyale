package me.shirasemaru.mineroyale.listener

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.shirasemaru.mineroyale.Mineroyale
import me.shirasemaru.mineroyale.bootstrap.GameWorldProvider
import me.shirasemaru.mineroyale.bootstrap.OnlinePlayerProvider
import me.shirasemaru.mineroyale.config.BorderSettings
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.EnhancedDamageSettings
import me.shirasemaru.mineroyale.config.FinalPhaseSettings
import me.shirasemaru.mineroyale.config.GameSettings
import me.shirasemaru.mineroyale.config.PhaseSettings
import me.shirasemaru.mineroyale.game.GameManager
import me.shirasemaru.mineroyale.game.GameSession
import me.shirasemaru.mineroyale.game.GameState
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
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scheduler.BukkitScheduler
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerJoinListenerTest {

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `onPlayerJoin sends player to lobby when game is not running`() {
        val fixture = createFixture()
        val joiningPlayer = mockPlayer("joiner")
        val event = mockJoinEvent(joiningPlayer)
        val listener = PlayerJoinListener(fixture.gameManager)

        listener.onPlayerJoin(event)

        verify(exactly = 1) { fixture.playerSetupService.preparePlayerForLobby(joiningPlayer) }
        verify(exactly = 1) { fixture.messageService.sendLobbyWaitingMessage(joiningPlayer) }
        verify(exactly = 0) { fixture.playerSetupService.prepareLateJoinSpectator(any()) }
        assertFalse(fixture.playerRegistry.isSpectator(joiningPlayer))
    }

    @Test
    fun `onPlayerJoin registers spectator and refreshes targets when game is running`() {
        mockkStatic(Bukkit::class)

        val fixture = createFixture()
        val alivePlayer = mockPlayer("alive")
        val joiningPlayer = mockPlayer("joiner")
        val event = mockJoinEvent(joiningPlayer)
        val listener = PlayerJoinListener(fixture.gameManager)
        val spectatorsSlot = slot<Collection<Player>>()
        val alivePlayersSlot = slot<Collection<Player>>()

        prepareRunningMatch(fixture.gameManager, fixture.playerRegistry, listOf(alivePlayer))
        every { Bukkit.getPlayer(any<UUID>()) } answers {
            when (firstArg<UUID>()) {
                alivePlayer.uniqueId -> alivePlayer
                joiningPlayer.uniqueId -> joiningPlayer
                else -> null
            }
        }
        every {
            fixture.spectatorService.refreshTargets(
                capture(spectatorsSlot),
                capture(alivePlayersSlot),
                any()
            )
        } just runs

        listener.onPlayerJoin(event)

        verify(exactly = 1) { fixture.playerSetupService.prepareLateJoinSpectator(joiningPlayer) }
        verify(exactly = 1) { fixture.messageService.sendLateJoinSpectatorMessage(joiningPlayer) }
        assertTrue(fixture.playerRegistry.isSpectator(joiningPlayer))
        assertFalse(fixture.playerRegistry.isAlive(joiningPlayer))
        assertTrue(spectatorsSlot.captured.contains(joiningPlayer))
        assertTrue(alivePlayersSlot.captured.contains(alivePlayer))
    }

    private fun createFixture(): Fixture {
        val plugin = mockk<Mineroyale>()
        val server = mockk<Server>()
        val scheduler = mockk<BukkitScheduler>(relaxed = true)
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
        val scoreboardManager = mockk<ScoreboardManager>(relaxed = true)
        val victoryService = mockk<VictoryService>(relaxed = true)
        val compassTrackingService = mockk<CompassTrackingService>(relaxed = true)
        val endCrystalService = mockk<EndCrystalService>(relaxed = true)
        val deathMarkerService = mockk<DeathMarkerService>(relaxed = true)
        val world = mockk<World>()
        val border = mockk<WorldBorder>(relaxed = true)

        every { plugin.namespace() } returns "mineroyale"
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { worldProvider.require() } returns world
        every { worldProvider.find() } returns world
        every { configManager.gameSettings } returns GameSettings(
            minPlayers = 2,
            maxPlayers = 10,
            countdownSeconds = 15,
            initialPvpGraceSeconds = 30,
            showPlayerLocatorBar = true,
            playerLocatorMaxAlivePlayers = 4,
            giveInitialCompass = true,
            giveEndCrystal = true,
            endCrystalGlowSeconds = 15,
            endCrystalItemName = "発光の岩",
            endCrystalItemDescription = "使用すると%seconds%秒間自分以外の生存者1名をランダムで発光させます。",
            hideNameTags = false,
            disableAdvancementAnnouncements = false,
            restrictBlockModificationOutsideBorder = false
        )
        every { configManager.borderSettings } returns BorderSettings(
            warningDistance = 10,
            warningTime = 5,
            phases = listOf(PhaseSettings(waitSeconds = 1, durationSeconds = 1, targetSize = 80.0)),
            finalPhase = FinalPhaseSettings(enabled = false, moveRange = 0.0, moveDurationSeconds = 0),
            enhancedDamage = EnhancedDamageSettings(
                enabled = false,
                baseDamage = 1.0,
                increasePerSecond = 0.5,
                maxDamage = 10.0
            )
        )
        every { world.worldBorder } returns border
        every { countdownService.cancel(any()) } just runs
        every { playerSetupService.preparePlayerForLobby(any()) } just runs
        every { playerSetupService.prepareLateJoinSpectator(any()) } just runs
        every { spectatorService.refreshTargets(any(), any(), any()) } just runs
        every { messageService.sendLobbyWaitingMessage(any()) } just runs
        every { messageService.sendLateJoinSpectatorMessage(any()) } just runs
        every { messageService.spectatorHeadDisplayName(any()) } answers { Component.text(firstArg<String>()) }

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
            messageService = messageService
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
        }
    }

    private fun sessionOf(gameManager: GameManager): GameSession {
        val holderField = GameManager::class.java.getDeclaredField("matchScopeHolder")
        holderField.isAccessible = true
        val holder = holderField.get(gameManager) as MatchScopeHolder
        return holder.current.session
    }

    private fun mockJoinEvent(player: Player): PlayerJoinEvent {
        val event = mockk<PlayerJoinEvent>()
        every { event.player } returns player
        return event
    }

    private fun mockPlayer(name: String): Player {
        val player = mockk<Player>()
        every { player.name } returns name
        every { player.uniqueId } returns UUID.nameUUIDFromBytes(name.toByteArray())
        every { player.isOnline } returns true
        every { player.location } returns Location(null, 0.0, 64.0, 0.0)
        return player
    }

    private data class Fixture(
        val gameManager: GameManager,
        val playerRegistry: PlayerRegistry,
        val playerSetupService: PlayerSetupService,
        val spectatorService: SpectatorService,
        val messageService: MessageService
    )

    private object EmptyOnlinePlayerProvider : OnlinePlayerProvider {
        override val onlinePlayers: Collection<Player> = emptyList()
    }
}
