package me.shirasemaru.mineroyale.service.game

import io.mockk.mockk
import me.shirasemaru.mineroyale.config.GameSettings
import me.shirasemaru.mineroyale.game.MatchRuleSnapshot
import org.bukkit.GameRule
import org.bukkit.World
import kotlin.test.Test
import kotlin.test.assertEquals

class GameRuleServiceTest {

    @Test
    fun `applyMatchRules returns empty snapshot when target game rules are unavailable`() {
        val service = GameRuleService(UnavailableGameRuleResolver)

        val snapshot = service.applyMatchRules(
            world = mockk(relaxed = true),
            settings = gameSettings(
                showPlayerLocatorBar = false,
                disableAdvancementAnnouncements = true
            )
        )

        assertEquals(MatchRuleSnapshot(locatorBar = null, advancementAnnouncements = null), snapshot)
    }

    @Test
    fun `restoreMatchRules safely ignores unavailable game rules`() {
        val service = GameRuleService(UnavailableGameRuleResolver)

        service.restoreMatchRules(
            world = mockk(relaxed = true),
            snapshot = MatchRuleSnapshot(locatorBar = true, advancementAnnouncements = false)
        )
    }

    private fun gameSettings(
        showPlayerLocatorBar: Boolean,
        disableAdvancementAnnouncements: Boolean
    ): GameSettings =
        GameSettings(
            minPlayers = 2,
            maxPlayers = 20,
            countdownSeconds = 30,
            initialPvpGraceSeconds = 45,
            showPlayerLocatorBar = showPlayerLocatorBar,
            playerLocatorMaxAlivePlayers = 4,
            enableCompassTracking = true,
            giveInitialCompass = true,
            giveEndCrystal = true,
            endCrystalGlowSeconds = 15,
            endCrystalItemName = "発光の岩",
            endCrystalItemDescription = "使用すると%seconds%秒間自分以外の生存者1名をランダムで発光させます。",
            hideNameTags = false,
            disableAdvancementAnnouncements = disableAdvancementAnnouncements,
            restrictBlockModificationOutsideBorder = false
        )

    private object UnavailableGameRuleResolver : GameRuleResolver {
        override fun locatorBar(): GameRule<Boolean>? = null

        override fun showAdvancementMessages(): GameRule<Boolean>? = null
    }
}
