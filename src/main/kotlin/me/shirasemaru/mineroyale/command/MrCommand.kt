package me.shirasemaru.mineroyale.command

import me.shirasemaru.mineroyale.game.GameManager
import me.shirasemaru.mineroyale.service.game.MessageService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MrCommand(
    private val gameManager: GameManager,
    private val messageService: MessageService
) : CommandExecutor, TabCompleter {

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

        if (!sender.hasPermission("mineroyale.admin")) {
            messageService.sendNoPermissionMessage(sender)
            return true
        }

        if (args.isEmpty()) {
            messageService.sendCommandUsageMessage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "start" -> {
                if (!gameManager.canStartNewGame()) {
                    messageService.sendCannotStartMessage(sender)
                    return true
                }

                messageService.broadcastCountdownStartRequested()
                gameManager.startGame()
            }

            "stop" -> {
                if (!gameManager.canStopGame()) {
                    messageService.sendCannotStopMessage(sender)
                    return true
                }

                gameManager.stopGame()
            }

            "reload" -> {
                gameManager.reloadConfig()
                messageService.sendConfigReloadedMessage(sender)
            }

            else -> messageService.sendUnknownSubcommandMessage(sender)
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("start", "stop", "reload")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        return emptyList()
    }
}
