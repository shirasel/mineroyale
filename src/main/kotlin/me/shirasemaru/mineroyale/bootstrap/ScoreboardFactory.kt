package me.shirasemaru.mineroyale.bootstrap

import me.shirasemaru.mineroyale.ui.ScoreboardManager
import org.bukkit.Bukkit

interface ScoreboardFactory {
    fun create(): ScoreboardManager
}

class BukkitScoreboardFactory(
    private val onlinePlayerProvider: OnlinePlayerProvider
) : ScoreboardFactory {
    override fun create(): ScoreboardManager =
        ScoreboardManager(
            onlinePlayerProvider = onlinePlayerProvider,
            scoreboardProvider = { Bukkit.getScoreboardManager().newScoreboard },
            mainScoreboardProvider = { Bukkit.getScoreboardManager().mainScoreboard }
        )
}
