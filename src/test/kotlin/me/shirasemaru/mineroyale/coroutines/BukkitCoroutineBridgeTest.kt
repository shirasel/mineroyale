package me.shirasemaru.mineroyale.coroutines

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class BukkitCoroutineBridgeTest {

    @Test
    fun `awaitChunkPreload resumes when chunk future fails`() = runBlocking {
        val world = mockk<World>()
        val location = Location(world, 0.0, 64.0, 0.0)
        val failure = IllegalStateException("chunk failed")
        val future = CompletableFuture<Chunk>()
        future.completeExceptionally(failure)
        val failures = mutableListOf<Throwable>()

        every { world.getChunkAtAsync(location, true) } returns future

        awaitChunkPreload(listOf(location), failures::add)

        assertSame(failure, failures.single())
    }

    @Test
    fun `awaitChunkPreload resumes when chunk request throws`() = runBlocking {
        val world = mockk<World>()
        val location = Location(world, 0.0, 64.0, 0.0)
        val failure = IllegalStateException("chunk request failed")
        val failures = mutableListOf<Throwable>()

        every { world.getChunkAtAsync(location, true) } throws failure

        awaitChunkPreload(listOf(location), failures::add)

        assertSame(failure, failures.single())
    }

    @Test
    fun `awaitChunkPreload ignores locations without worlds`() = runBlocking {
        val failures = mutableListOf<Throwable>()

        awaitChunkPreload(listOf(Location(null, 0.0, 64.0, 0.0)), failures::add)

        assertTrue(failures.isEmpty())
    }
}
