package me.shirasemaru.mineroyale12111.service.border

import me.shirasemaru.mineroyale12111.config.ConfigManager
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import kotlin.math.floor
import kotlin.random.Random

class SpawnPointService(
    private val configManager: ConfigManager,
    private val nextCoordinate: (Double, Double) -> Double = { from, to -> Random.nextDouble(from, to) }
) {

    fun generateRandomSpawnLocations(
        players: List<Player>,
        border: WorldBorder
    ): Map<Player, Location> =
        generateRandomSpawnLocations(
            players = players,
            plan = MatchBorderPlan(
                centerX = border.center.x,
                centerZ = border.center.z,
                size = border.size
            )
        )

    fun generateRandomSpawnLocations(
        players: List<Player>,
        plan: MatchBorderPlan
    ): Map<Player, Location> {
        val world = configManager.gameWorld
        val result = mutableMapOf<Player, Location>()
        val usedLocations = mutableListOf<Location>()

        val center = Location(world, plan.centerX, 0.0, plan.centerZ)
        val radius = plan.size / 2 - 1
        val minDistance = configManager.spawnSettings.minDistance
        val minDistanceSq = minDistance * minDistance

        for (player in players) {
            var location: Location? = null
            var attempts = 0

            while (attempts < 50) {
                val x = center.x + nextCoordinate(-radius, radius)
                val z = center.z + nextCoordinate(-radius, radius)
                val y = world.getHighestBlockYAt(x.toInt(), z.toInt())
                val candidate = Location(world, x, (y + 1).toDouble(), z)
                attempts++

                if (!isSafeSpawn(candidate)) continue
                if (usedLocations.any { it.distanceSquared(candidate) < minDistanceSq }) continue

                location = candidate
                break
            }

            if (location == null) {
                val y = world.getHighestBlockYAt(center.x.toInt(), center.z.toInt())
                location = Location(world, center.x, (y + 2).toDouble(), center.z)
            }

            usedLocations.add(location)
            result[player] = location
        }

        return result
    }

    private fun isSafeSpawn(location: Location): Boolean {
        val world = location.world ?: return false
        val ground = world.getBlockAt(location.blockX, floor(location.y - 1.0).toInt(), location.blockZ).type
        return ground != Material.LAVA &&
            ground != Material.WATER &&
            ground != Material.CACTUS &&
            ground != Material.FIRE &&
            ground != Material.CAMPFIRE
    }
}
