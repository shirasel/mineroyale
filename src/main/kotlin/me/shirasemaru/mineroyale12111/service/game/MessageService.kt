package me.shirasemaru.mineroyale12111.service.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Duration
import java.util.logging.Logger

class MessageService {

    fun spectatorHeadDisplayName(targetName: String): Component =
        Component.text("$targetName を観戦")

    fun sendPlayersOnlyCommandMessage(sender: CommandSender) {
        sender.sendMessage("このコマンドはプレイヤーのみ実行できます。")
    }

    fun sendNoPermissionMessage(sender: CommandSender) {
        sender.sendMessage("§cこのコマンドを実行する権限がありません。")
    }

    fun sendCommandUsageMessage(sender: CommandSender) {
        sender.sendMessage("§e使用方法: /mr <start|stop|reload>")
    }

    fun sendCannotStartMessage(sender: CommandSender) {
        sender.sendMessage("§c現在は新しいゲームを開始できません。")
    }

    fun broadcastCountdownStartRequested() {
        Bukkit.broadcast(Component.text("カウントダウンを開始します。", NamedTextColor.GREEN))
    }

    fun sendCannotStopMessage(sender: CommandSender) {
        sender.sendMessage("§c停止できるゲームがありません。")
    }

    fun sendConfigReloadedMessage(sender: CommandSender) {
        sender.sendMessage("設定を再読み込みしました。")
    }

    fun sendUnknownSubcommandMessage(sender: CommandSender) {
        sender.sendMessage("§c不明なサブコマンドです。")
    }

    fun broadcastNotEnoughPlayersToStart() {
        Bukkit.broadcast(Component.text("参加人数が不足しているため開始できません。"))
    }

    fun broadcastTooManyPlayers(maxPlayers: Int) {
        Bukkit.broadcast(Component.text("最大参加人数は ${maxPlayers} 人です。"))
    }

    fun broadcastGameStopped() {
        Bukkit.broadcast(Component.text("ゲームを停止しました。"))
    }

    fun broadcastCountdown(time: Int, players: List<Player>) {
        Bukkit.broadcast(Component.text("ゲーム開始まで $time 秒"))
        players.forEach {
            it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
        }
    }

    fun broadcastCountdownCancelled() {
        Bukkit.broadcast(Component.text("人数不足のためカウントダウンを中止しました。"))
    }

    fun broadcastGameStarting() {
        Bukkit.broadcast(Component.text("ゲームを開始します。"))
    }

    fun logMatchStarted(logger: Logger) {
        logger.info("Mineroyale: ゲームを開始しました。")
    }

    fun broadcastNoWinner() {
        Bukkit.broadcast(Component.text("ゲーム終了。勝者は決まりませんでした。"))
    }

    fun broadcastPlayerEliminated(playerName: String, aliveCount: Int) {
        Bukkit.broadcast(Component.text("$playerName が脱落しました。残り $aliveCount 人"))
    }

    fun sendLateJoinSpectatorMessage(player: Player) {
        player.sendMessage("現在ゲーム進行中のため、観戦モードで参加しました。")
    }

    fun sendLobbyWaitingMessage(player: Player) {
        player.sendMessage("ゲーム待機中です。開始をお待ちください。")
    }

    fun sendSpectatorTargetChanged(player: Player, targetName: String) {
        player.sendMessage("観戦対象を $targetName に切り替えました。")
    }

    fun broadcastPvpGracePeriod(seconds: Int) {
        Bukkit.broadcast(Component.text("PvP は ${seconds} 秒後に有効になります。"))
    }

    fun broadcastPvpEnabled(players: Iterable<Player>) {
        Bukkit.broadcast(Component.text("PvP が有効になりました。"))
        players.forEach {
            it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
        }
    }

    fun broadcastBorderPhase(phaseNumber: Int, waitSeconds: Int, targetSize: Double, durationSeconds: Int) {
        Bukkit.broadcast(Component.text("========== フェーズ $phaseNumber =========="))

        if (waitSeconds > 0) {
            Bukkit.broadcast(Component.text("${waitSeconds} 秒後に縮小が始まります。"))
        }

        Bukkit.broadcast(Component.text("ボーダーは ${targetSize} まで縮小します。"))
        Bukkit.broadcast(Component.text("縮小時間: ${durationSeconds} 秒"))
    }

    fun broadcastFinalMoveStarted() {
        Bukkit.broadcast(Component.text("最終フェーズが移動を開始しました。"))
    }

    fun broadcastVictory(winnerName: String) {
        Bukkit.broadcast(Component.text("${winnerName} が勝利しました。"))
    }

    fun victoryTitle(winnerName: String): Title =
        Title.title(
            Component.text("VICTORY!"),
            Component.text("${winnerName} の勝利"),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(3),
                Duration.ofMillis(1000)
            )
        )
}
