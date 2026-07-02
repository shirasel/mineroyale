package me.shirasemaru.mineroyale.service.game

import me.shirasemaru.mineroyale.config.GameSettings
import me.shirasemaru.mineroyale.game.MatchRuleSnapshot
import org.bukkit.GameRule
import org.bukkit.World

class GameRuleService(
    private val ruleResolver: GameRuleResolver = ReflectionGameRuleResolver()
) {

    fun applyMatchRules(world: World, settings: GameSettings): MatchRuleSnapshot {
        val originalLocatorBar = ruleResolver.locatorBar()?.let { rule ->
            world.getGameRuleValue(rule).also {
                world.setGameRule(rule, settings.showPlayerLocatorBar)
            }
        }

        val originalAdvancementAnnouncements =
            if (!settings.disableAdvancementAnnouncements) {
                null
            } else {
                ruleResolver.showAdvancementMessages()?.let { rule ->
                    world.getGameRuleValue(rule).also {
                        world.setGameRule(rule, false)
                    }
                }
            }

        return MatchRuleSnapshot(
            locatorBar = originalLocatorBar,
            advancementAnnouncements = originalAdvancementAnnouncements
        )
    }

    fun restoreMatchRules(world: World, snapshot: MatchRuleSnapshot?) {
        snapshot?.locatorBar?.let { originalValue ->
            ruleResolver.locatorBar()?.let { rule ->
                world.setGameRule(rule, originalValue)
            }
        }

        snapshot?.advancementAnnouncements?.let { originalValue ->
            ruleResolver.showAdvancementMessages()?.let { rule ->
                world.setGameRule(rule, originalValue)
            }
        }
    }
}

interface GameRuleResolver {
    fun locatorBar(): GameRule<Boolean>?

    fun showAdvancementMessages(): GameRule<Boolean>?
}

class ReflectionGameRuleResolver : GameRuleResolver {
    override fun locatorBar(): GameRule<Boolean>? =
        booleanRule("LOCATOR_BAR")

    override fun showAdvancementMessages(): GameRule<Boolean>? =
        booleanRule("SHOW_ADVANCEMENT_MESSAGES")

    private fun booleanRule(name: String): GameRule<Boolean>? =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            Class.forName("org.bukkit.GameRules")
                .getField(name)
                .get(null) as GameRule<Boolean>
        }.getOrNull()
}
