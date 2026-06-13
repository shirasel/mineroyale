package me.shirasemaru.mineroyale.ui

import me.shirasemaru.mineroyale.game.GameSession
import me.shirasemaru.mineroyale.game.GameState
import me.shirasemaru.mineroyale.game.PhaseState
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

class ScoreboardManager {

    private companion object {
        const val PLAYER_TEAM_NAME = "mr_players"
    }

    private val scoreboard: Scoreboard =
        Bukkit.getScoreboardManager().newScoreboard

    private val objective: Objective =
        scoreboard.registerNewObjective(
            "mineroyale",
            Criteria.DUMMY,
            Component.text("MineRoyale")
        )

    private var hideNameTags = false
    private var currentLines = emptyMap<Int, String>()
    private var currentTeamEntries = emptySet<String>()

    init {
        objective.displaySlot = DisplaySlot.SIDEBAR
    }

    fun setNameTagsHidden(hidden: Boolean) {
        hideNameTags = hidden
        syncPlayerTeam(Bukkit.getOnlinePlayers())
    }

    fun update(session: GameSession) {
        val desiredLines = buildLines(session)
        syncScores(desiredLines)

        val onlinePlayers = Bukkit.getOnlinePlayers()
        syncPlayerTeam(onlinePlayers)
        onlinePlayers.forEach { player ->
            if (player.scoreboard != scoreboard) {
                player.scoreboard = scoreboard
            }
        }
    }

    fun clear() {
        currentLines.values.forEach(scoreboard::resetScores)
        currentLines = emptyMap()
        currentTeamEntries = emptySet()
        hideNameTags = false
        scoreboard.getTeam(PLAYER_TEAM_NAME)?.unregister()
        Bukkit.getOnlinePlayers().forEach {
            it.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        }
    }

    private fun buildLines(session: GameSession): Map<Int, String> {
        val stateDisplay = when (session.state) {
            GameState.WAITING -> "待機中"
            GameState.COUNTDOWN -> "開始準備"
            GameState.RUNNING -> "ゲーム中"
            GameState.ENDING -> "終了演出"
        }

        val isFinalMoving = session.phaseState == PhaseState.FINAL_MOVING.displayName
        val phaseDisplay = if (isFinalMoving) {
            "最終"
        } else {
            "${session.currentPhase} / ${session.totalPhases}"
        }
        val phaseRemainingLabel = if (isFinalMoving) "次の移動" else "残り"
        val gameRemainingDisplay = if (isFinalMoving) {
            "最終フェーズ"
        } else {
            formatTime(session.remainingGameSeconds)
        }

        return linkedMapOf(
            9 to "§7状態: §e$stateDisplay",
            8 to "§7生存者: §e${session.aliveCount}",
            7 to "§f",
            6 to "§7フェーズ: §e$phaseDisplay",
            5 to "§7進行: §e${session.phaseState}",
            4 to "§7$phaseRemainingLabel: §e${formatTime(session.remainingPhaseSeconds)}",
            3 to "§r",
            2 to "§7全体残り: §e$gameRemainingDisplay"
        )
    }

    private fun syncScores(desiredLines: Map<Int, String>) {
        currentLines.forEach { (score, entry) ->
            val nextEntry = desiredLines[score]
            if (nextEntry != entry) {
                scoreboard.resetScores(entry)
            }
        }

        desiredLines.forEach { (score, entry) ->
            if (currentLines[score] != entry) {
                objective.getScore(entry).score = score
            }
        }

        currentLines = desiredLines
    }

    private fun syncPlayerTeam(players: Collection<Player>) {
        val existingTeam = scoreboard.getTeam(PLAYER_TEAM_NAME)
        if (!hideNameTags) {
            currentTeamEntries = emptySet()
            existingTeam?.unregister()
            return
        }

        val desiredEntries = players.mapTo(linkedSetOf(), Player::getName)
        val team = existingTeam ?: scoreboard.registerNewTeam(PLAYER_TEAM_NAME)
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)

        if (currentTeamEntries == desiredEntries) {
            return
        }

        currentTeamEntries.minus(desiredEntries).forEach(team::removeEntry)
        desiredEntries.minus(currentTeamEntries).forEach(team::addEntry)
        currentTeamEntries = desiredEntries
    }

    private fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }
}
