package me.shirasemaru.mineroyale.command

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import me.shirasemaru.mineroyale.game.GameManager
import me.shirasemaru.mineroyale.service.game.MessageService
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
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

    private fun createFixture(playerSender: Boolean, hasPermission: Boolean): Fixture {
        val gameManager = mockk<GameManager>(relaxed = true)
        val messageService = mockk<MessageService>(relaxed = true)
        val sender = if (playerSender) {
            mockk<Player>().also { player ->
                every { player.hasPermission(any<String>()) } returns hasPermission
            }
        } else {
            mockk<CommandSender>()
        }
        val bukkitCommand = mockk<Command>()
        val command = MrCommand(gameManager, messageService)

        return Fixture(
            command = command,
            gameManager = gameManager,
            messageService = messageService,
            sender = sender,
            bukkitCommand = bukkitCommand
        )
    }

    private data class Fixture(
        val command: MrCommand,
        val gameManager: GameManager,
        val messageService: MessageService,
        val sender: CommandSender,
        val bukkitCommand: Command
    )
}
