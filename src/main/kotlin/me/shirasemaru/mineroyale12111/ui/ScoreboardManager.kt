package me.shirasemaru.mineroyale12111.ui

import me.shirasemaru.mineroyale12111.game.GameState
import org.bukkit.Bukkit
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard

class ScoreboardManager {

    private val scoreboard: Scoreboard =
        Bukkit.getScoreboardManager().newScoreboard

    private val objective: Objective =
        scoreboard.registerNewObjective(
            "mineroyale",
            "dummy",
            "§6マインロワイヤル"
        )

    init {
        objective.displaySlot = DisplaySlot.SIDEBAR
    }

    fun updateAll(
        gameState: GameState,
        aliveCount: Int,
        remainingGameSeconds: Int,
        currentPhase: Int,
        totalPhases: Int,
        phaseState: String,
        remainingPhaseSeconds: Int
    ) {
        clear()

        objective.getScore("§7状態: §e$gameState").score = 9
        objective.getScore("§7生存者: §e$aliveCount").score = 8
        objective.getScore(" ").score = 7

        objective.getScore("§7フェーズ: §e$currentPhase / $totalPhases").score = 6
        objective.getScore("§7状態: §e$phaseState").score = 5
        objective.getScore("§7フェーズ残り: §e${formatTime(remainingPhaseSeconds)}").score = 4
        objective.getScore("  ").score = 3

        objective.getScore("§7全体残り: §e${formatTime(remainingGameSeconds)}").score = 2

        Bukkit.getOnlinePlayers().forEach {
            it.scoreboard = scoreboard
        }
    }

    private fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun clear() {
        scoreboard.entries.forEach {
            scoreboard.resetScores(it)
        }
    }
}