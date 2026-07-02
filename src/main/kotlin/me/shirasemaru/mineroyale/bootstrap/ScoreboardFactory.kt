package me.shirasemaru.mineroyale.bootstrap

import me.shirasemaru.mineroyale.ui.ScoreboardManager

interface ScoreboardFactory {
    fun create(): ScoreboardManager
}

class BukkitScoreboardFactory(
    private val onlinePlayerProvider: OnlinePlayerProvider,
    private val scoreboardProvider: ScoreboardProvider
) : ScoreboardFactory {
    override fun create(): ScoreboardManager =
        ScoreboardManager(
            onlinePlayerProvider = onlinePlayerProvider,
            scoreboardProvider = { scoreboardProvider.newScoreboard },
            mainScoreboardProvider = { scoreboardProvider.mainScoreboard }
        )
}
