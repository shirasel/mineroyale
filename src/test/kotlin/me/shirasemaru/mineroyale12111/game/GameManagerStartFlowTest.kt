package me.shirasemaru.mineroyale12111.game

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import me.shirasemaru.mineroyale12111.Mineroyale12111
import me.shirasemaru.mineroyale12111.config.BorderSettings
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.config.EnhancedDamageSettings
import me.shirasemaru.mineroyale12111.config.FinalPhaseSettings
import me.shirasemaru.mineroyale12111.config.GameSettings
import me.shirasemaru.mineroyale12111.config.PhaseSettings
import me.shirasemaru.mineroyale12111.config.SpawnSettings
import me.shirasemaru.mineroyale12111.config.WorldSettings
import me.shirasemaru.mineroyale12111.service.game.CountdownService
import me.shirasemaru.mineroyale12111.service.game.MessageService
import me.shirasemaru.mineroyale12111.service.game.VictoryService
import me.shirasemaru.mineroyale12111.service.item.EndCrystalService
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import me.shirasemaru.mineroyale12111.service.player.PlayerSetupService
import me.shirasemaru.mineroyale12111.service.player.SpectatorService
import me.shirasemaru.mineroyale12111.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameManagerStartFlowTest {

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `startGame moves from countdown to running when countdown completes`() {
        mockkStatic(Bukkit::class)

        val fixture = createFixture()
        val playerB = mockPlayer("bravo")
        val playerA = mockPlayer("alpha")
        val participants = linkedSetOf(playerB, playerA)
        val onCompletedSlot = slot<(List<Player>) -> Unit>()

        every { Bukkit.getOnlinePlayers() } returns participants
        every { Bukkit.getScheduler() } returns fixture.scheduler
        every {
            fixture.countdownService.start(
                session = any(),
                seconds = 15,
                minPlayers = 2,
                participantProvider = any(),
                onParticipantsChanged = any(),
                onTick = any(),
                onCancelled = any(),
                onCompleted = capture(onCompletedSlot)
            )
        } just runs

        fixture.gameManager.startGame()

        assertEquals(GameState.COUNTDOWN, fixture.gameManager.getState())
        assertEquals(2, sessionOf(fixture.gameManager).participantCount)
        verify(exactly = 1) {
            fixture.countdownService.start(
                session = any(),
                seconds = 15,
                minPlayers = 2,
                participantProvider = any(),
                onParticipantsChanged = any(),
                onTick = any(),
                onCancelled = any(),
                onCompleted = any()
            )
        }
        verify(exactly = 0) { fixture.messageService.broadcastGameStarting() }

        onCompletedSlot.captured.invoke(listOf(playerA, playerB))

        assertEquals(GameState.RUNNING, fixture.gameManager.getState())
        assertEquals(2, fixture.playerRegistry.aliveCount())
        assertEquals(2, sessionOf(fixture.gameManager).participantCount)
        assertEquals(2, sessionOf(fixture.gameManager).aliveCount)
        verify(exactly = 1) { fixture.messageService.broadcastGameStarting() }
        verify(timeout = 1_000, exactly = 2) { fixture.playerSetupService.prepareMatchPlayer(any(), any()) }
        verify(timeout = 1_000, exactly = 1) { fixture.endCrystalService.distribute(any()) }
        verify(timeout = 1_000, exactly = 1) { fixture.messageService.logMatchStarted(any()) }
        verify(timeout = 1_000, exactly = 1) { fixture.compassTrackingService.start(any()) }
        verify(timeout = 1_000, exactly = 1) { fixture.scheduler.runTaskTimer(any<JavaPlugin>(), any<Runnable>(), 0L, 20L) }
    }

    private fun createFixture(): StartFlowFixture {
        val plugin = mockk<Mineroyale12111>()
        val server = mockk<Server>()
        val scheduler = mockk<BukkitScheduler>()
        val scoreboardTask = mockk<BukkitTask>()
        val border = mockBorder()
        val world = mockWorld(border)
        val configManager = mockk<ConfigManager>()
        val playerRegistry = PlayerRegistry()
        val playerSetupService = mockk<PlayerSetupService>()
        val spectatorService = mockk<SpectatorService>(relaxed = true)
        val countdownService = mockk<CountdownService>()
        val messageService = mockk<MessageService>(relaxed = true)
        val scoreboardManager = mockk<ScoreboardManager>()
        val victoryService = mockk<VictoryService>(relaxed = true)
        val compassTrackingService = mockk<CompassTrackingService>()
        val endCrystalService = mockk<EndCrystalService>(relaxed = true)

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getLogger("test")
        every { plugin.namespace() } returns "mineroyale12111"
        every { server.scheduler } returns scheduler
        every { scheduler.runTaskTimer(any<JavaPlugin>(), any<Runnable>(), any<Long>(), any<Long>()) } returns scoreboardTask
        every { scheduler.runTask(any<JavaPlugin>(), any<Runnable>()) } answers {
            (invocation.args[1] as Runnable).run()
            mockk(relaxed = true)
        }
        every { scheduler.runTaskLater(any<JavaPlugin>(), any<Runnable>(), any<Long>()) } answers {
            mockk(relaxed = true)
        }
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
        every { configManager.worldSettings } returns WorldSettings(
            name = "world",
            randomCenterRange = 1000.0,
            initialBorderSize = 100.0
        )
        every { configManager.spawnSettings } returns SpawnSettings(
            minDistance = 0.0
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
        every { configManager.gameWorld } returns world
        every { countdownService.cancel(any()) } just runs
        every { playerSetupService.prepareMatchPlayer(any(), any()) } just runs
        every { compassTrackingService.start(any()) } just runs
        every { scoreboardManager.update(any()) } just runs
        every { scoreboardManager.setNameTagsHidden(any()) } just runs

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
            endCrystalService = endCrystalService
        )

        return StartFlowFixture(
            gameManager = gameManager,
            playerRegistry = playerRegistry,
            playerSetupService = playerSetupService,
            countdownService = countdownService,
            messageService = messageService,
            compassTrackingService = compassTrackingService,
            scheduler = scheduler,
            endCrystalService = endCrystalService
        )
    }

    private fun mockWorld(border: WorldBorder): World {
        val chunk = mockk<Chunk>()
        val world = mockk<World>()
        every { world.worldBorder } returns border
        every { world.players } returns emptyList()
        every { world.getChunkAtAsync(any<Location>(), true) } returns CompletableFuture.completedFuture(chunk)
        every { world.getHighestBlockYAt(any<Int>(), any<Int>()) } returns 64
        every { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) } answers {
            val block = mockk<Block>()
            every { block.type } returns Material.GRASS_BLOCK
            block
        }
        return world
    }

    private fun mockBorder(): WorldBorder {
        var size = 100.0
        var centerX = 0.0
        var centerZ = 0.0
        var warningDistance = 0

        val border = mockk<WorldBorder>()
        every { border.size } answers { size }
        every { border.size = any() } answers { size = firstArg() }
        every { border.center } answers { Location(null, centerX, 0.0, centerZ) }
        every { border.setCenter(any<Double>(), any<Double>()) } answers {
            centerX = firstArg()
            centerZ = secondArg()
        }
        every { border.warningDistance } answers { warningDistance }
        every { border.warningDistance = any() } answers { warningDistance = firstArg() }
        every { border.setWarningTimeTicks(any()) } just runs
        return border
    }

    private fun mockPlayer(name: String): Player {
        val inventory = mockk<PlayerInventory>(relaxed = true)
        val player = mockk<Player>()
        every { player.name } returns name
        every { player.uniqueId } returns UUID.nameUUIDFromBytes(name.toByteArray())
        every { player.gameMode } returns GameMode.SURVIVAL
        every { player.isOnline } returns true
        every { player.inventory } returns inventory
        return player
    }

    private data class StartFlowFixture(
        val gameManager: GameManager,
        val playerRegistry: PlayerRegistry,
        val playerSetupService: PlayerSetupService,
        val countdownService: CountdownService,
        val messageService: MessageService,
        val compassTrackingService: CompassTrackingService,
        val scheduler: BukkitScheduler,
        val endCrystalService: EndCrystalService
    )
}

private fun sessionOf(gameManager: GameManager): GameSession {
    val field = GameManager::class.java.getDeclaredField("session")
    field.isAccessible = true
    return field.get(gameManager) as GameSession
}
