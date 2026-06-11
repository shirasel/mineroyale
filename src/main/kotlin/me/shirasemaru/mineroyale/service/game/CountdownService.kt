package me.shirasemaru.mineroyale.service.game

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import me.shirasemaru.mineroyale.coroutines.waitTicks
import me.shirasemaru.mineroyale.game.GameSession
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class CountdownService(
    private val plugin: JavaPlugin
) {

    sealed interface CountdownResult {
        data class Cancelled(val participants: List<Player>) : CountdownResult
        data class Completed(val participants: List<Player>) : CountdownResult
    }

    private var countdownJob: Job? = null

    suspend fun run(
        session: GameSession,
        seconds: Int,
        minPlayers: Int,
        participantProvider: () -> List<Player>,
        onParticipantsChanged: (List<Player>) -> Unit,
        onTick: (Int, List<Player>) -> Unit
    ): CountdownResult {
        cancel(session)

        val currentJob = currentCoroutineContext()[Job]
        countdownJob = currentJob

        var time = seconds
        var lastParticipantIds: List<UUID>? = null
        session.countdownRemainingSeconds = seconds

        try {
            while (true) {
                val participants = participantProvider()
                val participantIds = participants.map(Player::getUniqueId)
                session.participantCount = participants.size

                if (lastParticipantIds != participantIds) {
                    onParticipantsChanged(participants)
                    lastParticipantIds = participantIds
                }

                if (participants.size < minPlayers) {
                    return CountdownResult.Cancelled(participants)
                }

                if (time <= 0) {
                    return CountdownResult.Completed(participants)
                }

                session.countdownRemainingSeconds = time
                onTick(time, participants)
                time--
                plugin.waitTicks(20L)
            }
        } finally {
            if (countdownJob == currentJob) {
                countdownJob = null
            }
            session.countdownRemainingSeconds = 0
        }
    }

    fun cancel(session: GameSession? = null) {
        countdownJob?.cancel()
        countdownJob = null
        session?.countdownRemainingSeconds = 0
    }

    fun isRunning(): Boolean = countdownJob != null
}
