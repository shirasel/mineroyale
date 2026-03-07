package me.shirasemaru.mineroyale12111.command

import me.shirasemaru.mineroyale12111.game.GameManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MrCommand(
    private val gameManager: GameManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。")
            return true
        }

        if (!sender.hasPermission("mineroyale.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§e使用方法: /mr <start|stop|reload>")
            return true
        }

        when (args[0].lowercase()) {

            "start" -> {

                if (gameManager.isRunning()) {
                    sender.sendMessage("§cゲームは既に開始されています。")
                    return true
                }

                sender.server.broadcastMessage("§aゲームを開始します！")
                gameManager.startGame()
            }

            "stop" -> {

                if (!gameManager.isRunning()) {
                    sender.sendMessage("§c現在ゲームは実行中ではありません。")
                    return true
                }

                gameManager.endGame(null)
                sender.server.broadcastMessage("§c管理者によりゲームが強制終了されました。")
            }

            "reload" -> {

                gameManager.reloadConfig()
                sender.sendMessage("§aconfigをリロードしました。")
            }

            else -> sender.sendMessage("§c不明なサブコマンドです。")
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