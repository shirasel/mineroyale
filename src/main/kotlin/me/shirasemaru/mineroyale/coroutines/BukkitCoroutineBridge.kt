package me.shirasemaru.mineroyale.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class BukkitDispatcher(
    private val plugin: JavaPlugin
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        plugin.server.scheduler.runTask(plugin, block)
    }
}

suspend fun JavaPlugin.nextTick() =
    suspendCancellableCoroutine { continuation ->
        server.scheduler.runTask(this, Runnable {
            continuation.resume(Unit)
        })
    }

suspend fun JavaPlugin.waitTicks(ticks: Long) =
    suspendCancellableCoroutine { continuation ->
        server.scheduler.runTaskLater(this, Runnable {
            continuation.resume(Unit)
        }, ticks)
    }

suspend fun awaitChunkPreload(locations: Collection<Location>) =
    suspendCancellableCoroutine { continuation ->
        val futures = locations
            .mapNotNull { location -> location.world?.getChunkAtAsync(location, true) }
            .distinct()

        if (futures.isEmpty() || futures.all { it.isDone }) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        CompletableFuture.allOf(*futures.toTypedArray())
            .thenRun { continuation.resume(Unit) }
    }
