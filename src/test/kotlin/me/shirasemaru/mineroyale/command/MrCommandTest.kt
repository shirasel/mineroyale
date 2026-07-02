package me.shirasemaru.mineroyale.command

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import me.shirasemaru.mineroyale.bootstrap.OfflinePlayerResolver
import me.shirasemaru.mineroyale.game.GameManager
import me.shirasemaru.mineroyale.service.game.MessageService
import me.shirasemaru.mineroyale.service.player.MineRoyalePermissionService
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MrCommandTest {

    @Test
    fun `start command broadcasts countdown request and starts game when start is allowed`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)
        every { fixture.gameManager.canStartNewGame() } returns true
        every { fixture.gameManager.startGame() } just runs
        every { fixture.messageService.broadcastCountdownStartRequested() } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("start")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.messageService.broadcastCountdownStartRequested() }
        verify(exactly = 1) { fixture.gameManager.startGame() }
    }

    @Test
    fun `stop command stops game when stopping is allowed`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)
        every { fixture.gameManager.canStopGame() } returns true
        every { fixture.gameManager.stopGame() } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("stop")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.gameManager.stopGame() }
        verify(exactly = 0) { fixture.messageService.sendCannotStopMessage(any()) }
    }

    @Test
    fun `reload command reloads config and notifies sender`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)
        every { fixture.gameManager.reloadConfig() } just runs
        every { fixture.messageService.sendConfigReloadedMessage(fixture.sender) } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("reload")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.gameManager.reloadConfig() }
        verify(exactly = 1) { fixture.messageService.sendConfigReloadedMessage(fixture.sender) }
    }

    @Test
    fun `command rejects sender without permission`() {
        val fixture = createFixture(playerSender = true, hasPermission = false)
        every { fixture.messageService.sendNoPermissionMessage(fixture.sender) } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("start")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.messageService.sendNoPermissionMessage(fixture.sender) }
        verify(exactly = 0) { fixture.gameManager.startGame() }
    }

    @Test
    fun `command shows usage when no arguments are given`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)
        every { fixture.messageService.sendCommandUsageMessage(fixture.sender) } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            emptyArray()
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.messageService.sendCommandUsageMessage(fixture.sender) }
    }

    @Test
    fun `command rejects non player sender`() {
        val fixture = createFixture(playerSender = false, hasPermission = false)
        every { fixture.messageService.sendPlayersOnlyCommandMessage(fixture.sender) } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("start")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.messageService.sendPlayersOnlyCommandMessage(fixture.sender) }
        verify(exactly = 0) { fixture.gameManager.startGame() }
    }

    @Test
    fun `tab completion filters supported subcommands`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)

        val result = fixture.command.onTabComplete(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("re")
        )

        assertEquals(listOf("reload"), result)
    }

    @Test
    fun `addop command grants internal permission to offline player`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)
        val target = mockk<OfflinePlayer>()
        val targetId = UUID.nameUUIDFromBytes("target".toByteArray())

        every { target.name } returns "target"
        every { target.uniqueId } returns targetId
        every { fixture.offlinePlayerResolver.resolve("target") } returns target
        every { fixture.permissionService.grant(targetId, "target", PermissionNodes.COMMAND_START) } just runs
        every {
            fixture.messageService.sendMineRoyalePermissionGrantedMessage(
                fixture.sender,
                "target",
                "start"
            )
        } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("addop", "target", "start")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.permissionService.grant(targetId, "target", PermissionNodes.COMMAND_START) }
        verify(exactly = 1) {
            fixture.messageService.sendMineRoyalePermissionGrantedMessage(fixture.sender, "target", "start")
        }
    }

    @Test
    fun `deop command revokes all internal permissions from offline player`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)
        val target = mockk<OfflinePlayer>()
        val targetId = UUID.nameUUIDFromBytes("target".toByteArray())

        every { target.name } returns "target"
        every { target.uniqueId } returns targetId
        every { fixture.offlinePlayerResolver.resolve("target") } returns target
        every { fixture.permissionService.revokeAll(targetId) } just runs
        every {
            fixture.messageService.sendMineRoyalePermissionsRevokedMessage(fixture.sender, "target")
        } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("deop", "target")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.permissionService.revokeAll(targetId) }
        verify(exactly = 1) {
            fixture.messageService.sendMineRoyalePermissionsRevokedMessage(fixture.sender, "target")
        }
    }

    @Test
    fun `addop command uses typed name when offline player name is unavailable`() {
        val fixture = createFixture(playerSender = true, hasPermission = true)
        val target = mockk<OfflinePlayer>()
        val targetId = UUID.nameUUIDFromBytes("unknown".toByteArray())

        every { target.name } returns null
        every { target.uniqueId } returns targetId
        every { fixture.offlinePlayerResolver.resolve("unknown") } returns target
        every { fixture.permissionService.grant(targetId, "unknown", PermissionNodes.COMMAND_RELOAD) } just runs
        every {
            fixture.messageService.sendMineRoyalePermissionGrantedMessage(
                fixture.sender,
                "unknown",
                "reload"
            )
        } just runs

        val result = fixture.command.onCommand(
            fixture.sender,
            fixture.bukkitCommand,
            "mr",
            arrayOf("addop", "unknown", "reload")
        )

        assertTrue(result)
        verify(exactly = 1) { fixture.permissionService.grant(targetId, "unknown", PermissionNodes.COMMAND_RELOAD) }
        verify(exactly = 1) {
            fixture.messageService.sendMineRoyalePermissionGrantedMessage(fixture.sender, "unknown", "reload")
        }
    }

    private fun createFixture(playerSender: Boolean, hasPermission: Boolean): Fixture {
        val gameManager = mockk<GameManager>(relaxed = true)
        val messageService = mockk<MessageService>(relaxed = true)
        val permissionService = mockk<MineRoyalePermissionService>()
        val offlinePlayerResolver = mockk<OfflinePlayerResolver>()
        val sender = if (playerSender) {
            mockk<Player>().also { player ->
                every { player.hasPermission(any<String>()) } returns hasPermission
                every { permissionService.has(player, any()) } returns hasPermission
            }
        } else {
            mockk<CommandSender>()
        }
        val bukkitCommand = mockk<Command>()
        val command = MrCommand(gameManager, messageService, permissionService, offlinePlayerResolver)

        return Fixture(
            command = command,
            gameManager = gameManager,
            messageService = messageService,
            permissionService = permissionService,
            offlinePlayerResolver = offlinePlayerResolver,
            sender = sender,
            bukkitCommand = bukkitCommand
        )
    }

    private data class Fixture(
        val command: MrCommand,
        val gameManager: GameManager,
        val messageService: MessageService,
        val permissionService: MineRoyalePermissionService,
        val offlinePlayerResolver: OfflinePlayerResolver,
        val sender: CommandSender,
        val bukkitCommand: Command
    )
}
