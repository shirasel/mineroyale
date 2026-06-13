package me.shirasemaru.mineroyale.config

data class GameSettings(
    val minPlayers: Int,
    val maxPlayers: Int,
    val countdownSeconds: Int,
    val initialPvpGraceSeconds: Int,
    val showPlayerLocatorBar: Boolean,
    val playerLocatorMaxAlivePlayers: Int,
    val enableCompassTracking: Boolean = true,
    val giveInitialCompass: Boolean,
    val giveEndCrystal: Boolean,
    val endCrystalGlowSeconds: Int,
    val endCrystalItemName: String,
    val endCrystalItemDescription: String,
    val hideNameTags: Boolean,
    val disableAdvancementAnnouncements: Boolean,
    val restrictBlockModificationOutsideBorder: Boolean
)
