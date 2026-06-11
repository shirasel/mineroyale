package me.shirasemaru.mineroyale.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.shirasemaru.mineroyale.game.GameManager
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityRegainHealthEvent
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRegainListenerTest {

    @Test
    fun `natural regeneration is throttled and reduced during match`() {
        var now = 1_000L
        val gameManager = mockk<GameManager>()
        val player = mockPlayer()
        val listener = HealthRegainListener(gameManager) { now }
        val firstEvent = mockRegainEvent(player, EntityRegainHealthEvent.RegainReason.SATIATED, 6.0)
        val secondEvent = mockRegainEvent(player, EntityRegainHealthEvent.RegainReason.SATIATED, 6.0)
        val thirdEvent = mockRegainEvent(player, EntityRegainHealthEvent.RegainReason.SATIATED, 6.0)

        every { gameManager.isRunning() } returns true

        listener.onHealthRegain(firstEvent)
        now += 4_000L
        listener.onHealthRegain(secondEvent)
        now += 4_100L
        listener.onHealthRegain(thirdEvent)

        verify(exactly = 0) { firstEvent.isCancelled = any() }
        assertEquals(0.5, firstEvent.amount, 0.001)
        verify(exactly = 1) { secondEvent.isCancelled = true }
        assertEquals(6.0, secondEvent.amount, 0.001)
        verify(exactly = 0) { thirdEvent.isCancelled = any() }
        assertEquals(0.5, thirdEvent.amount, 0.001)
    }

    @Test
    fun `non satiated regeneration is left untouched while hunger stays maxed`() {
        val gameManager = mockk<GameManager>()
        val player = mockPlayer()
        val listener = HealthRegainListener(gameManager) { 1_000L }
        val event = mockRegainEvent(player, EntityRegainHealthEvent.RegainReason.MAGIC, 4.0)

        every { gameManager.isRunning() } returns true

        listener.onHealthRegain(event)

        verify(exactly = 0) { event.isCancelled = any() }
        assertEquals(4.0, event.amount, 0.001)
        assertEquals(20, player.foodLevel)
        assertEquals(20f, player.saturation)
    }

    private fun mockPlayer(): Player {
        var foodLevel = 0
        var saturation = 0f
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.foodLevel } answers { foodLevel }
        every { player.foodLevel = any() } answers { foodLevel = firstArg() }
        every { player.saturation } answers { saturation }
        every { player.saturation = any() } answers { saturation = firstArg() }
        return player
    }

    private fun mockRegainEvent(
        player: Player,
        reason: EntityRegainHealthEvent.RegainReason,
        initialAmount: Double
    ): EntityRegainHealthEvent {
        var amount = initialAmount
        val event = mockk<EntityRegainHealthEvent>()
        every { event.entity } returns player
        every { event.regainReason } returns reason
        every { event.isCancelled = any() } returns Unit
        every { event.amount } answers { amount }
        every { event.amount = any() } answers { amount = firstArg() }
        return event
    }
}
