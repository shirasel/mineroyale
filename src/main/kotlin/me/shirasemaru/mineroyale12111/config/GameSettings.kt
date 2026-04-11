package me.shirasemaru.mineroyale12111.config

data class GameSettings(
    val minPlayers: Int,
    val maxPlayers: Int,
    val countdownSeconds: Int,
    val initialPvpGraceSeconds: Int,
    val showPlayerLocatorBar: Boolean,
    val giveInitialCompass: Boolean,
    val hideNameTags: Boolean,
    val disableAdvancementAnnouncements: Boolean,
    val restrictBlockModificationOutsideBorder: Boolean
)
