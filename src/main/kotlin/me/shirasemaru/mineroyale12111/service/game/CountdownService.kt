package me.shirasemaru.mineroyale12111.service.game

import me.shirasemaru.mineroyale12111.game.GameSession
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class CountdownService(
    private val plugin: JavaPlugin
) {

    private var countdownTask: BukkitTask? = null

    fun start(
        session: GameSession,
        seconds: Int,
        minPlayers: Int,
        participantProvider: () -> List<Player>,
        onParticipantsChanged: (List<Player>) -> Unit,
        onTick: (Int, List<Player>) -> Unit,
        onCancelled: (List<Player>) -> Unit,
        onCompleted: (List<Player>) -> Unit
    ) {
        cancel(session)

        var time = seconds
        var lastParticipantIds: List<UUID>? = null
        session.countdownRemainingSeconds = seconds

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val participants = participantProvider()
            val participantIds = participants.map(Player::getUniqueId)
            session.participantCount = participants.size

            if (lastParticipantIds != participantIds) {
                onParticipantsChanged(participants)
                lastParticipantIds = participantIds
            }

            if (participants.size < minPlayers) {
                cancel(session)
                onCancelled(participants)
                return@Runnable
            }

            if (time <= 0) {
                cancel(session)
                onCompleted(participants)
                return@Runnable
            }

            session.countdownRemainingSeconds = time
            onTick(time, participants)
            time--
        }, 0L, 20L)
    }

    fun cancel(session: GameSession? = null) {
        countdownTask?.cancel()
        countdownTask = null
        session?.countdownRemainingSeconds = 0
    }

    fun isRunning(): Boolean = countdownTask != null
}
