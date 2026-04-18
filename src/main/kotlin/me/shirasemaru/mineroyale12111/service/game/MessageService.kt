package me.shirasemaru.mineroyale12111.service.game

import net.kyori.adventure.text.Component
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
        sender.sendMessage("このコマンドはプレイヤーのみ使用できます。")
        playWarningSound(sender)
    }

    fun sendNoPermissionMessage(sender: CommandSender) {
        sender.sendMessage("このコマンドを使用する権限がありません。")
        playWarningSound(sender)
    }

    fun sendCommandUsageMessage(sender: CommandSender) {
        sender.sendMessage("使用方法: /mr <start|stop|reload>")
        playInfoSound(sender)
    }

    fun sendCannotStartMessage(sender: CommandSender) {
        sender.sendMessage("現在は新しいゲームを開始できません。")
        playWarningSound(sender)
    }

    fun broadcastCountdownStartRequested() {
        Bukkit.broadcast(Component.text("カウントダウンを開始します。"))
        playStartSound(Bukkit.getOnlinePlayers())
    }

    fun sendCannotStopMessage(sender: CommandSender) {
        sender.sendMessage("停止できるゲームがありません。")
        playWarningSound(sender)
    }

    fun sendConfigReloadedMessage(sender: CommandSender) {
        sender.sendMessage("設定を再読み込みしました。")
        playInfoSound(sender)
    }

    fun sendUnknownSubcommandMessage(sender: CommandSender) {
        sender.sendMessage("不明なサブコマンドです。")
        playWarningSound(sender)
    }

    fun broadcastNotEnoughPlayersToStart() {
        Bukkit.broadcast(Component.text("参加人数が不足しているため開始できません。"))
        playWarningSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastTooManyPlayers(maxPlayers: Int) {
        Bukkit.broadcast(Component.text("最大参加人数は ${maxPlayers} 人です。"))
        playWarningSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastGameStopped() {
        Bukkit.broadcast(Component.text("ゲームを終了しました。"))
        playInfoSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastCountdown(time: Int, players: List<Player>) {
        Bukkit.broadcast(Component.text("ゲーム開始まで $time 秒"))
        players.forEach {
            it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
        }
    }

    fun broadcastCountdownCancelled() {
        Bukkit.broadcast(Component.text("参加人数不足のためカウントダウンを中止しました。"))
        playWarningSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastGameStarting() {
        Bukkit.broadcast(Component.text("ゲームを開始します。"))
        playStartSound(Bukkit.getOnlinePlayers())
    }

    fun logMatchStarted(logger: Logger) {
        logger.info("Mineroyale: ゲームを開始しました。")
    }

    fun broadcastNoWinner() {
        Bukkit.broadcast(Component.text("ゲーム終了。勝者はいませんでした。"))
        playInfoSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastPlayerEliminated(playerName: String, aliveCount: Int) {
        Bukkit.broadcast(Component.text("$playerName が脱落しました。残り $aliveCount 人"))
        playAlertSound(Bukkit.getOnlinePlayers())
    }

    fun sendLateJoinSpectatorMessage(player: Player) {
        player.sendMessage("現在ゲーム進行中のため、観戦モードで参加しました。")
        playInfoSound(player)
    }

    fun sendLobbyWaitingMessage(player: Player) {
        player.sendMessage("ゲーム待機中です。開始をお待ちください。")
        playInfoSound(player)
    }

    fun sendSpectatorTargetChanged(player: Player, targetName: String) {
        player.sendMessage("観戦対象を $targetName に切り替えました。")
        playInfoSound(player)
    }

    fun sendSpectatorOnlyCommandMessage(player: Player) {
        player.sendMessage("このコマンドは観戦者のみ使用できます。")
        playWarningSound(player)
    }

    fun sendEndCrystalReceived(player: Player) {
        player.sendMessage("エンドクリスタルを受け取りました。右クリックで使用できます。")
        playInfoSound(player)
    }

    fun sendEndCrystalNoTarget(player: Player) {
        player.sendMessage("発光させる対象がいません。")
        playWarningSound(player)
    }

    fun sendEndCrystalUsed(player: Player, targetName: String, seconds: Int) {
        player.sendMessage("$targetName を ${seconds} 秒間発光させました。")
        playAlertSound(listOf(player))
    }

    fun sendEndCrystalMarked(player: Player, seconds: Int) {
        player.sendMessage("エンドクリスタルの効果で ${seconds} 秒間発光状態になりました。")
        playAlertSound(listOf(player))
    }

    fun broadcastPvpGracePeriod(seconds: Int) {
        Bukkit.broadcast(Component.text("PvP は ${seconds} 秒後に有効になります。"))
        playAlertSound(Bukkit.getOnlinePlayers())
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
            Bukkit.broadcast(Component.text("${waitSeconds} 秒後にエリア収縮が始まります。"))
        }

        Bukkit.broadcast(Component.text("ボーダーは ${targetSize} まで収縮します。"))
        Bukkit.broadcast(Component.text("収縮時間: ${durationSeconds} 秒"))
        playAlertSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastBorderShrinkStarted(phaseNumber: Int, targetSize: Double, durationSeconds: Int) {
        Bukkit.broadcast(Component.text("フェーズ $phaseNumber のエリア収縮が始まりました。"))
        Bukkit.broadcast(Component.text("ボーダーは ${targetSize} まで ${durationSeconds} 秒で収縮します。"))
        playAlertSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastFinalMoveStarted() {
        Bukkit.broadcast(Component.text("最終フェーズの移動を開始しました。"))
        playAlertSound(Bukkit.getOnlinePlayers())
    }

    fun broadcastVictory(winnerName: String) {
        Bukkit.broadcast(Component.text("${winnerName} が勝利しました。"))
        playVictorySound(Bukkit.getOnlinePlayers())
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

    private fun playWarningSound(sender: CommandSender) {
        if (sender is Player) {
            playWarningSound(sender)
        }
    }

    private fun playInfoSound(sender: CommandSender) {
        if (sender is Player) {
            playInfoSound(sender)
        }
    }

    private fun playWarningSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f)
    }

    private fun playInfoSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.05f)
    }

    private fun playInfoSound(players: Iterable<Player>) {
        players.forEach(::playInfoSound)
    }

    private fun playWarningSound(players: Iterable<Player>) {
        players.forEach(::playWarningSound)
    }

    private fun playStartSound(players: Iterable<Player>) {
        players.forEach {
            it.playSound(it.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f)
        }
    }

    private fun playAlertSound(players: Iterable<Player>) {
        players.forEach {
            it.playSound(it.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.35f)
        }
    }

    private fun playVictorySound(players: Iterable<Player>) {
        players.forEach {
            it.playSound(it.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.6f)
        }
    }
}
