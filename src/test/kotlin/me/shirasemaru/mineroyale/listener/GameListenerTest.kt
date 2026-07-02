package me.shirasemaru.mineroyale.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.shirasemaru.mineroyale.config.ConfigManager
import me.shirasemaru.mineroyale.config.GameSettings
import me.shirasemaru.mineroyale.game.GameManager
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.projectiles.ProjectileSource
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class GameListenerTest {

    @Test
    fun `onDeath schedules handlePlayerDeath for next tick while game is running`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val scheduler = mockk<BukkitScheduler>()
        val task = mockk<BukkitTask>(relaxed = true)
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val player = mockPlayer()
        val deathLocation = Location(mockk<World>(), 10.0, 64.0, -5.0)
        val event = mockk<PlayerDeathEvent>()
        var scheduled: Runnable? = null

        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { gameManager.isRunning() } returns true
        every { event.entity } returns player
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.location } returns deathLocation
        every { scheduler.runTaskLater(plugin, any<Runnable>(), 1L) } answers {
            scheduled = secondArg()
            task
        }
        every { gameManager.handlePlayerDeath(player, deathLocation) } returns Unit

        listener.onDeath(event)

        verify(exactly = 1) { scheduler.runTaskLater(plugin, any<Runnable>(), 1L) }
        verify(exactly = 0) { gameManager.handlePlayerDeath(any(), any()) }

        scheduled!!.run()

        verify(exactly = 1) { gameManager.handlePlayerDeath(player, deathLocation) }
    }

    @Test
    fun `onQuit and onKick delegate to handlePlayerDeath only while running`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val player = mockPlayer()
        val quitEvent = mockk<PlayerQuitEvent>()
        val kickEvent = mockk<PlayerKickEvent>()

        every { quitEvent.player } returns player
        every { kickEvent.player } returns player
        every { gameManager.handlePlayerDeath(player) } returns Unit

        every { gameManager.isRunning() } returns false
        listener.onQuit(quitEvent)
        listener.onKick(kickEvent)
        verify(exactly = 0) { gameManager.handlePlayerDeath(player) }

        every { gameManager.isRunning() } returns true
        listener.onQuit(quitEvent)
        listener.onKick(kickEvent)
        verify(exactly = 2) { gameManager.handlePlayerDeath(player) }
    }

    @Test
    fun `onDamage cancels pvp damage when game is running and pvp is disabled`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val attacker = mockPlayer()
        val victim = mockPlayer()
        val event = mockDamageEvent(attacker, victim)

        every { gameManager.isProtectedDeathMarker(victim) } returns false
        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(victim) } returns false
        every { gameManager.isSpectator(attacker) } returns false
        every { gameManager.isPvpEnabled() } returns false

        listener.onDamage(event)

        verify(exactly = 1) { event.setCancelled(true) }
    }

    @Test
    fun `onDamage cancels attacks against protected death markers even outside match flow`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val attacker = mockPlayer()
        val marker = mockk<Entity>()
        val event = mockDamageEvent(attacker, marker)

        every { gameManager.isProtectedDeathMarker(marker) } returns true

        listener.onDamage(event)

        verify(exactly = 1) { event.setCancelled(true) }
        verify(exactly = 0) { gameManager.isRunning() }
    }

    @Test
    fun `onDamage does not cancel when attacker is not a player source`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val nonPlayerProjectile = mockk<Projectile>()
        val nonPlayerSource = mockk<ProjectileSource>()
        val victim = mockPlayer()
        val event = mockDamageEvent(nonPlayerProjectile, victim)

        every { gameManager.isProtectedDeathMarker(victim) } returns false
        every { gameManager.isRunning() } returns true
        every { nonPlayerProjectile.shooter } returns nonPlayerSource

        listener.onDamage(event)

        verify(exactly = 0) { event.setCancelled(any()) }
        verify(exactly = 0) { gameManager.isPvpEnabled() }
    }

    @Test
    fun `onDamage cancels when attacker or victim is spectator`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val attacker = mockPlayer()
        val victim = mockPlayer()
        val event = mockDamageEvent(attacker, victim)

        every { gameManager.isProtectedDeathMarker(victim) } returns false
        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(victim) } returns false
        every { gameManager.isSpectator(attacker) } returns true

        listener.onDamage(event)

        verify(exactly = 1) { event.setCancelled(true) }
        verify(exactly = 0) { gameManager.isPvpEnabled() }
    }

    @Test
    fun `onBlockPlace and onBlockBreak cancel modifications outside border when enabled`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager(restrictBlockModificationOutsideBorder = true)
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val outsideLocation = Location(mockk<World>(), 200.0, 64.0, 200.0)
        val placeEvent = mockBlockPlaceEvent(outsideLocation)
        val breakEvent = mockBlockBreakEvent(outsideLocation)

        every { gameManager.isRunning() } returns true
        every { gameManager.isOutsideCurrentBorder(outsideLocation) } returns true

        listener.onBlockPlace(placeEvent)
        listener.onBlockBreak(breakEvent)

        verify(exactly = 1) { placeEvent.setCancelled(true) }
        verify(exactly = 1) { breakEvent.setCancelled(true) }
    }

    @Test
    fun `onBlockPlace ignores border check when restriction is disabled`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager(restrictBlockModificationOutsideBorder = false)
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val outsideLocation = Location(mockk<World>(), 200.0, 64.0, 200.0)
        val placeEvent = mockBlockPlaceEvent(outsideLocation)

        every { gameManager.isRunning() } returns true

        listener.onBlockPlace(placeEvent)

        verify(exactly = 0) { placeEvent.setCancelled(any()) }
        verify(exactly = 0) { gameManager.isOutsideCurrentBorder(any()) }
    }

    @Test
    fun `onRespawn restores death location and spectator mode for spectators`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val player = mockPlayer()
        val deathEvent = mockk<PlayerDeathEvent>()
        val respawnEvent = mockRespawnEvent(player)
        val deathLocation = Location(mockk<World>(), 3.0, 70.0, 8.0)

        every { gameManager.isRunning() } returns true
        every { deathEvent.entity } returns player
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.location } returns deathLocation
        every { gameManager.respawnOverrideLocation() } returns null
        every { gameManager.isSpectator(player) } returns true
        every { gameManager.reapplySpectatorMode(player) } returns Unit

        val scheduler = mockk<BukkitScheduler>()
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { scheduler.runTaskLater(plugin, any<Runnable>(), 1L) } answers {
            secondArg<Runnable>().run()
            mockk(relaxed = true)
        }
        every { gameManager.handlePlayerDeath(player, deathLocation) } returns Unit

        listener.onDeath(deathEvent)
        listener.onRespawn(respawnEvent)

        assertEquals(deathLocation, respawnEvent.respawnLocation)
        verify(exactly = 1) { gameManager.reapplySpectatorMode(player) }
    }

    @Test
    fun `onRespawn uses victory location while ending`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val player = mockPlayer()
        val respawnEvent = mockRespawnEvent(player)
        val victoryLocation = Location(mockk<World>(), 25.0, 80.0, -14.0)

        every { gameManager.isRunning() } returns false
        every { gameManager.respawnOverrideLocation() } returns victoryLocation

        listener.onRespawn(respawnEvent)

        assertEquals(victoryLocation, respawnEvent.respawnLocation)
        verify(exactly = 0) { gameManager.reapplySpectatorMode(any()) }
    }

    @Test
    fun `onMove observes border damage targets only while running and changing block`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val player = mockPlayer()
        val event = mockMoveEvent(
            player = player,
            from = Location(mockk<World>(), 0.0, 64.0, 0.0),
            to = Location(mockk<World>(), 1.0, 64.0, 0.0)
        )

        every { gameManager.isRunning() } returns false
        every { gameManager.observeBorderDamageTarget(player) } returns Unit
        listener.onMove(event)
        verify(exactly = 0) { gameManager.observeBorderDamageTarget(any()) }

        every { gameManager.isRunning() } returns true
        listener.onMove(event)
        verify(exactly = 1) { gameManager.observeBorderDamageTarget(player) }
    }

    @Test
    fun `onArmorStandManipulate cancels interaction with protected death markers`() {
        val plugin = mockk<JavaPlugin>()
        val configManager = mockConfigManager()
        val gameManager = mockk<GameManager>()
        val listener = GameListener(plugin, configManager, gameManager, testCoroutineScope())
        val armorStand = mockk<ArmorStand>()
        val event = mockArmorStandManipulateEvent(armorStand)

        every { gameManager.isProtectedDeathMarker(armorStand) } returns true

        listener.onArmorStandManipulate(event)

        verify(exactly = 1) { event.isCancelled = true }
    }

    private fun mockPlayer(): Player {
        var gameMode = GameMode.SURVIVAL
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.gameMode } answers { gameMode }
        every { player.gameMode = any() } answers { gameMode = firstArg() }
        return player
    }

    private fun mockDamageEvent(damager: Any, victim: Entity): EntityDamageByEntityEvent {
        val event = mockk<EntityDamageByEntityEvent>()
        every { event.entity } returns victim
        every { event.damager } returns damager as Entity
        every { event.setCancelled(any()) } returns Unit
        return event
    }

    private fun mockRespawnEvent(player: Player): PlayerRespawnEvent {
        var respawnLocation = Location(mockk<World>(), 0.0, 64.0, 0.0)
        val event = mockk<PlayerRespawnEvent>()
        every { event.player } returns player
        every { event.respawnLocation } answers { respawnLocation }
        every { event.respawnLocation = any() } answers { respawnLocation = firstArg() }
        return event
    }

    private fun mockBlockPlaceEvent(location: Location): BlockPlaceEvent {
        val block = mockk<Block>()
        val event = mockk<BlockPlaceEvent>()
        every { block.location } returns location
        every { event.block } returns block
        every { event.setCancelled(any()) } returns Unit
        return event
    }

    private fun mockBlockBreakEvent(location: Location): BlockBreakEvent {
        val block = mockk<Block>()
        val event = mockk<BlockBreakEvent>()
        every { block.location } returns location
        every { event.block } returns block
        every { event.setCancelled(any()) } returns Unit
        return event
    }

    private fun mockMoveEvent(player: Player, from: Location, to: Location): PlayerMoveEvent {
        val event = mockk<PlayerMoveEvent>()
        every { event.player } returns player
        every { event.from } returns from
        every { event.to } returns to
        return event
    }

    private fun mockArmorStandManipulateEvent(
        armorStand: ArmorStand
    ): PlayerArmorStandManipulateEvent {
        val event = mockk<PlayerArmorStandManipulateEvent>()
        every { event.rightClicked } returns armorStand
        every { event.isCancelled = any() } returns Unit
        return event
    }

    private fun mockConfigManager(
        restrictBlockModificationOutsideBorder: Boolean = false
    ): ConfigManager {
        val gameSettings = mockk<GameSettings>()
        val configManager = mockk<ConfigManager>()
        every { configManager.gameSettings } returns gameSettings
        every { gameSettings.restrictBlockModificationOutsideBorder } returns restrictBlockModificationOutsideBorder
        return configManager
    }

    private fun testCoroutineScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Unconfined)
}
