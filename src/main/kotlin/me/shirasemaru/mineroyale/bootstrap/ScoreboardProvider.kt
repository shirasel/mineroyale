package me.shirasemaru.mineroyale.bootstrap

import org.bukkit.Bukkit
import org.bukkit.scoreboard.Scoreboard

interface ScoreboardProvider {
    val newScoreboard: Scoreboard

    val mainScoreboard: Scoreboard
}

class BukkitScoreboardProvider : ScoreboardProvider {
    override val newScoreboard: Scoreboard
        get() = Bukkit.getScoreboardManager().newScoreboard

    override val mainScoreboard: Scoreboard
        get() = Bukkit.getScoreboardManager().mainScoreboard
}
