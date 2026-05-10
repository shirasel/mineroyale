package me.shirasemaru.mineroyale12111.bootstrap

import me.shirasemaru.mineroyale12111.ui.ScoreboardManager

interface ScoreboardFactory {
    fun create(): ScoreboardManager
}

class BukkitScoreboardFactory : ScoreboardFactory {
    override fun create(): ScoreboardManager = ScoreboardManager()
}
