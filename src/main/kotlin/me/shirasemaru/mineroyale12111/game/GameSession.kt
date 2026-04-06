package me.shirasemaru.mineroyale12111.game

data class GameSession(
    var state: GameState = GameState.WAITING,
    var pvpEnabled: Boolean = false,
    var participantCount: Int = 0,
    var aliveCount: Int = 0,
    var countdownRemainingSeconds: Int = 0,
    var currentPhase: Int = 0,
    var totalPhases: Int = 0,
    var phaseState: String = PhaseState.IDLE.displayName,
    var remainingPhaseSeconds: Int = 0,
    var remainingGameSeconds: Int = 0
) {
    fun resetToWaiting() {
        state = GameState.WAITING
        pvpEnabled = false
        participantCount = 0
        aliveCount = 0
        countdownRemainingSeconds = 0
        currentPhase = 0
        totalPhases = 0
        phaseState = PhaseState.IDLE.displayName
        remainingPhaseSeconds = 0
        remainingGameSeconds = 0
    }
}
