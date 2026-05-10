package me.shirasemaru.mineroyale12111.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
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
    suspendCancellableCoroutine<Unit> { continuation ->
        server.scheduler.runTask(this, Runnable {
            continuation.resume(Unit)
        })
    }

suspend fun JavaPlugin.awaitChunkPreload(locations: Collection<Location>) =
    suspendCancellableCoroutine<Unit> { continuation ->
        val futures = locations
            .mapNotNull { location -> location.world?.getChunkAtAsync(location, true) }
            .distinct()

        if (futures.isEmpty() || futures.all { it.isDone }) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        java.util.concurrent.CompletableFuture.allOf(*futures.toTypedArray())
            .thenRun { continuation.resume(Unit) }
    }
