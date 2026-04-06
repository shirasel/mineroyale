package me.shirasemaru.mineroyale12111.ui

import me.shirasemaru.mineroyale12111.game.GameSession
import me.shirasemaru.mineroyale12111.game.GameState
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard

class ScoreboardManager {

    private val scoreboard: Scoreboard =
        Bukkit.getScoreboardManager().newScoreboard

    private val objective: Objective =
        scoreboard.registerNewObjective(
            "mineroyale",
            Criteria.DUMMY,
            Component.text("MineRoyale")
        )

    init {
        objective.displaySlot = DisplaySlot.SIDEBAR
    }

    fun update(session: GameSession) {
        clearScores()

        val stateDisplay = when (session.state) {
            GameState.WAITING -> "待機中"
            GameState.COUNTDOWN -> "開始準備"
            GameState.RUNNING -> "ゲーム中"
            GameState.ENDING -> "終了演出"
        }

        objective.getScore("§7状態: §e$stateDisplay").score = 9
        objective.getScore("§7生存者: §e${session.aliveCount}").score = 8
        objective.getScore("§f").score = 7
        objective.getScore("§7フェーズ: §e${session.currentPhase} / ${session.totalPhases}").score = 6
        objective.getScore("§7進行: §e${session.phaseState}").score = 5
        objective.getScore("§7残り: §e${formatTime(session.remainingPhaseSeconds)}").score = 4
        objective.getScore("§r").score = 3
        objective.getScore("§7全体残り: §e${formatTime(session.remainingGameSeconds)}").score = 2

        Bukkit.getOnlinePlayers().forEach {
            it.scoreboard = scoreboard
        }
    }

    private fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun clearScores() {
        scoreboard.entries.forEach {
            scoreboard.resetScores(it)
        }
    }

    fun clear() {
        Bukkit.getOnlinePlayers().forEach {
            it.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        }
    }
}
