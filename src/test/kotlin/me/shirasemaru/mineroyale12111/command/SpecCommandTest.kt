package me.shirasemaru.mineroyale12111.command

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.service.game.MessageService
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.test.Test
import kotlin.test.assertTrue

class SpecCommandTest {

    @Test
    fun `spec command opens menu for spectator`() {
        val gameManager = mockk<GameManager>()
        val messageService = mockk<MessageService>(relaxed = true)
        val sender = mockk<Player>()
        val command = SpecCommand(gameManager, messageService)
        val bukkitCommand = mockk<Command>()

        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(sender) } returns true
        every { gameManager.openSpectatorMenu(sender) } just runs

        val result = command.onCommand(sender, bukkitCommand, "spec", emptyArray())

        assertTrue(result)
        verify(exactly = 1) { gameManager.openSpectatorMenu(sender) }
        verify(exactly = 0) { messageService.sendSpectatorOnlyCommandMessage(any()) }
    }

    @Test
    fun `spec command rejects non spectator player`() {
        val gameManager = mockk<GameManager>()
        val messageService = mockk<MessageService>(relaxed = true)
        val sender = mockk<Player>()
        val command = SpecCommand(gameManager, messageService)
        val bukkitCommand = mockk<Command>()

        every { gameManager.isRunning() } returns true
        every { gameManager.isSpectator(sender) } returns false
        every { messageService.sendSpectatorOnlyCommandMessage(sender) } just runs

        val result = command.onCommand(sender, bukkitCommand, "spec", emptyArray())

        assertTrue(result)
        verify(exactly = 1) { messageService.sendSpectatorOnlyCommandMessage(sender) }
        verify(exactly = 0) { gameManager.openSpectatorMenu(any()) }
    }

    @Test
    fun `spec command rejects non player sender`() {
        val gameManager = mockk<GameManager>()
        val messageService = mockk<MessageService>(relaxed = true)
        val sender = mockk<CommandSender>()
        val command = SpecCommand(gameManager, messageService)
        val bukkitCommand = mockk<Command>()

        every { messageService.sendPlayersOnlyCommandMessage(sender) } just runs

        val result = command.onCommand(sender, bukkitCommand, "spec", emptyArray())

        assertTrue(result)
        verify(exactly = 1) { messageService.sendPlayersOnlyCommandMessage(sender) }
        verify(exactly = 0) { gameManager.openSpectatorMenu(any()) }
    }
}
