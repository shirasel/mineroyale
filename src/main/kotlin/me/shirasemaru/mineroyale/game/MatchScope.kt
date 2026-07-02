package me.shirasemaru.mineroyale.game

import kotlinx.coroutines.Job
import me.shirasemaru.mineroyale.service.border.MatchBorderPlan
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

class MatchScope(
    val session: GameSession = GameSession()
) {
    var preparedSpawnLocations: Map<Player, Location>? = null
    var preparedSpawnPlayerIds: List<UUID>? = null
    var preparedBorderPlan: MatchBorderPlan? = null
    var victoryRespawnLocation: Location? = null
    var scoreboardJob: Job? = null
    var ruleSnapshot: MatchRuleSnapshot? = null

    fun clearPreparedSpawns() {
        preparedSpawnLocations = null
        preparedSpawnPlayerIds = null
        preparedBorderPlan = null
    }

    fun resetRuntime() {
        clearPreparedSpawns()
        victoryRespawnLocation = null
        ruleSnapshot = null
        scoreboardJob?.cancel()
        scoreboardJob = null
        session.resetToWaiting()
    }
}
