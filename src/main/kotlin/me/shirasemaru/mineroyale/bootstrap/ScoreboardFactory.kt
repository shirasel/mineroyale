package me.shirasemaru.mineroyale.bootstrap

import me.shirasemaru.mineroyale.ui.ScoreboardManager

interface ScoreboardFactory {
    fun create(): ScoreboardManager
}

class BukkitScoreboardFactory : ScoreboardFactory {
    override fun create(): ScoreboardManager = ScoreboardManager()
}
