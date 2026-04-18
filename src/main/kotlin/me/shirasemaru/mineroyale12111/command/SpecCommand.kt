package me.shirasemaru.mineroyale12111.command

import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.service.game.MessageService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SpecCommand(
    private val gameManager: GameManager,
    private val messageService: MessageService
) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            messageService.sendPlayersOnlyCommandMessage(sender)
            return true
        }

        if (!gameManager.isRunning() || !gameManager.isSpectator(sender)) {
            messageService.sendSpectatorOnlyCommandMessage(sender)
            return true
        }

        gameManager.openSpectatorMenu(sender)
        return true
    }
}
