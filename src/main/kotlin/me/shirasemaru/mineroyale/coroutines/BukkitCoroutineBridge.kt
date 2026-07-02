package me.shirasemaru.mineroyale.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
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

suspend fun awaitChunkPreload(
    locations: Collection<Location>,
    onFailure: (Throwable) -> Unit = {}
) =
    suspendCancellableCoroutine { continuation ->
        val futures = locations.mapNotNull { location ->
            runCatching {
                location.world?.getChunkAtAsync(location, true)
            }.onFailure(onFailure)
                .getOrNull()
        }
            .distinct()

        if (futures.isEmpty()) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        CompletableFuture.allOf(*futures.toTypedArray())
            .whenComplete { _, error ->
                error?.let { onFailure(it.unwrapCompletionException()) }
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
    }

private fun Throwable.unwrapCompletionException(): Throwable =
    if (this is CompletionException) cause ?: this else this
