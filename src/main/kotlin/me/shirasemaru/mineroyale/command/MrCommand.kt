package me.shirasemaru.mineroyale.command

import me.shirasemaru.mineroyale.game.GameManager
import me.shirasemaru.mineroyale.service.game.MessageService
import me.shirasemaru.mineroyale.service.player.MineRoyalePermissionService
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MrCommand(
    private val gameManager: GameManager,
    private val messageService: MessageService,
    private val permissionService: MineRoyalePermissionService
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

        if (args.isEmpty()) {
            messageService.sendCommandUsageMessage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "start" -> {
                if (!sender.hasCommandPermission(PermissionNodes.COMMAND_START)) {
                    messageService.sendNoPermissionMessage(sender)
                    return true
                }

                if (!gameManager.canStartNewGame()) {
                    messageService.sendCannotStartMessage(sender)
                    return true
                }

                messageService.broadcastCountdownStartRequested()
                gameManager.startGame()
            }

            "stop" -> {
                if (!sender.hasCommandPermission(PermissionNodes.COMMAND_STOP)) {
                    messageService.sendNoPermissionMessage(sender)
                    return true
                }

                if (!gameManager.canStopGame()) {
                    messageService.sendCannotStopMessage(sender)
                    return true
                }

                gameManager.stopGame()
            }

            "reload" -> {
                if (!sender.hasCommandPermission(PermissionNodes.COMMAND_RELOAD)) {
                    messageService.sendNoPermissionMessage(sender)
                    return true
                }

                gameManager.reloadConfig()
                messageService.sendConfigReloadedMessage(sender)
            }

            "addop" -> {
                if (!permissionService.has(sender, PermissionNodes.ADMIN) &&
                    !permissionService.has(sender, PermissionNodes.COMMAND_ADDOP)
                ) {
                    messageService.sendNoPermissionMessage(sender)
                    return true
                }

                addOp(sender, args)
            }

            "deop" -> {
                if (!permissionService.has(sender, PermissionNodes.ADMIN) &&
                    !permissionService.has(sender, PermissionNodes.COMMAND_DEOP)
                ) {
                    messageService.sendNoPermissionMessage(sender)
                    return true
                }

                deOp(sender, args)
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
            return listOf("start", "stop", "reload", "addop", "deop")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 3 && args[0].equals("addop", ignoreCase = true)) {
            return listOf("admin", "mr", "start", "stop", "reload", "addop", "deop")
                .filter { it.startsWith(args[2].lowercase()) }
        }

        return emptyList()
    }

    private fun Player.hasCommandPermission(permission: String): Boolean =
        permissionService.has(this, permission)

    private fun addOp(sender: Player, args: Array<out String>) {
        if (args.size != 3) {
            messageService.sendAddOpUsageMessage(sender)
            return
        }

        val targetName = args[1]
        val target = Bukkit.getOfflinePlayer(targetName)
        val resolvedTargetName = target.displayNameOr(targetName)

        val permission = PermissionNodes.resolve(args[2])
        if (permission == null) {
            messageService.sendInvalidMineRoyalePermissionMessage(sender, args[2])
            return
        }

        permissionService.grant(target.uniqueId, resolvedTargetName, permission)
        messageService.sendMineRoyalePermissionGrantedMessage(
            sender = sender,
            playerName = resolvedTargetName,
            permission = PermissionNodes.displayName(permission)
        )
    }

    private fun deOp(sender: Player, args: Array<out String>) {
        if (args.size != 2) {
            messageService.sendDeOpUsageMessage(sender)
            return
        }

        val targetName = args[1]
        val target = Bukkit.getOfflinePlayer(targetName)
        val resolvedTargetName = target.displayNameOr(targetName)

        permissionService.revokeAll(target.uniqueId)
        messageService.sendMineRoyalePermissionsRevokedMessage(sender, resolvedTargetName)
    }

    private fun OfflinePlayer.displayNameOr(fallback: String): String =
        name ?: fallback
}
