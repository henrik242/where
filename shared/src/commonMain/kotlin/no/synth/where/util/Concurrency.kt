package no.synth.where.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Maps in parallel with at most [limit] running at once, keeping order. */
suspend fun <T, R> List<T>.mapConcurrently(limit: Int = 6, transform: suspend (T) -> R): List<R> = coroutineScope {
    val semaphore = Semaphore(limit)
    map { async { semaphore.withPermit { transform(it) } } }.awaitAll()
}
