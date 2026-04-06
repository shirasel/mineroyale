package me.shirasemaru.mineroyale12111.config

data class BorderSettings(
    val warningDistance: Int,
    val warningTime: Int,
    val phases: List<PhaseSettings>,
    val finalPhase: FinalPhaseSettings,
    val enhancedDamage: EnhancedDamageSettings
)

data class FinalPhaseSettings(
    val enabled: Boolean,
    val moveRange: Double,
    val moveDurationSeconds: Int
)

data class EnhancedDamageSettings(
    val enabled: Boolean,
    val baseDamage: Double,
    val increasePerSecond: Double,
    val maxDamage: Double
)
