package no.synth.where.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

/** A download that will not change further: finished, gave up, or was cancelled. */
val DownloadStatus.isTerminal: Boolean
    get() = this == DownloadStatus.COMPLETED || this == DownloadStatus.FAILED || this == DownloadStatus.CANCELLED

data class QueuedDownload(
    val region: Region,
    val layerId: String,
    val layerDisplayName: String,
    /** Human-readable name shown to the user (area name + coordinate); [region] name stays the id. */
    val label: String = region.name,
    val minZoom: Int = 5,
    val maxZoom: Int,
    val downloadDem: Boolean,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val mapProgress: Int = 0,
    val demProgress: Int = -1,
) {
    /** Stable id; also the native pack key used by the platform download engines. */
    val id: String get() = "${region.name}-$layerId"

    /** Combined 0..100 for a single row bar. Equal-weight map/DEM when DEM is on. */
    val overallProgress: Int
        get() = if (demProgress < 0) mapProgress else (mapProgress + demProgress) / 2
}

/** Downloads one region to a terminal state. Platform-specific (native tiles + DEM). */
interface DownloadEngine {
    /** true = success, false = failure. Must propagate [CancellationException] cooperatively. */
    suspend fun download(item: QueuedDownload, onProgress: (mapPercent: Int, demPercent: Int) -> Unit): Boolean

    /** Best-effort stop of the currently running native download. No-op when idle. */
    fun cancelActive()
}

/**
 * Holds the download queue and processes it sequentially (one active download at a time).
 * [scope] must be single-thread/main-confined and [onActive]/[onIdle] non-suspending, so the
 * "restart the worker on enqueue" path cannot race with [drain] finishing.
 */
class DownloadQueueManager(
    private val engine: DownloadEngine,
    private val scope: CoroutineScope,
    private val onActive: () -> Unit = {},
    private val onIdle: () -> Unit = {},
    /**
     * Fail the active download if it makes no progress for this long, so it can't stall the queue.
     * Non-positive disables the watchdog (used by tests that hold downloads open deliberately).
     */
    private val stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
) {
    private val _queue = MutableStateFlow<List<QueuedDownload>>(emptyList())
    val queue: StateFlow<List<QueuedDownload>> = _queue.asStateFlow()

    private var worker: Job? = null
    private var activeDownload: Deferred<Boolean>? = null

    fun enqueue(item: QueuedDownload) {
        val existing = _queue.value.firstOrNull { it.id == item.id }
        if (existing?.status == DownloadStatus.QUEUED || existing?.status == DownloadStatus.DOWNLOADING) return
        val fresh = item.copy(
            status = DownloadStatus.QUEUED,
            mapProgress = 0,
            demProgress = if (item.downloadDem) 0 else -1,
        )
        _queue.update { list -> list.filterNot { it.id == item.id } + fresh }
        pump()
    }

    fun cancel(id: String) {
        val item = _queue.value.firstOrNull { it.id == id } ?: return
        if (item.status == DownloadStatus.DOWNLOADING) {
            setStatus(id, DownloadStatus.CANCELLED)
            engine.cancelActive()
            activeDownload?.cancel()
        } else {
            _queue.update { list -> list.filterNot { it.id == id } }
        }
    }

    fun clearFinished() {
        _queue.update { list -> list.filterNot { it.status.isTerminal } }
    }

    /**
     * Abort everything now (e.g. the Android foreground-service time budget was hit): stop the
     * active native download and mark every unfinished item FAILED so it stays visible. The user
     * can re-enqueue later; any tiles already fetched are reused from MapLibre's cache, though a
     * large partial pack may have been evicted and re-downloaded rather than truly resumed.
     */
    fun stopAll() {
        engine.cancelActive()
        worker?.cancel()
        worker = null
        activeDownload = null
        _queue.update { list ->
            list.map {
                if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED) {
                    it.copy(status = DownloadStatus.FAILED)
                } else {
                    it
                }
            }
        }
    }

    private fun pump() {
        if (worker?.isActive == true) return
        if (_queue.value.none { it.status == DownloadStatus.QUEUED }) return
        worker = scope.launch { drain() }
    }

    private suspend fun drain() = coroutineScope {
        onActive()
        try {
            while (true) {
                val next = _queue.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: break
                setStatus(next.id, DownloadStatus.DOWNLOADING)
                val deferred = async {
                    engine.download(next) { map, dem ->
                        updateItem(next.id) { it.copy(mapProgress = map, demProgress = if (it.downloadDem) dem else -1) }
                    }
                }
                activeDownload = deferred
                // Watchdog: fail the item if its progress value doesn't move for stallTimeoutMs, so
                // one stuck region can't block the whole queue. We compare the actual percent, not
                // callback frequency, since some engines report progress on a fixed timer.
                val watchdog = if (stallTimeoutMs > 0) {
                    launch {
                        val progressOf = { _queue.value.firstOrNull { it.id == next.id }?.let { it.mapProgress + it.demProgress } ?: 0 }
                        var last = progressOf()
                        while (true) {
                            delay(stallTimeoutMs)
                            val now = progressOf()
                            if (now == last) {
                                setStatus(next.id, DownloadStatus.FAILED)
                                engine.cancelActive()
                                deferred.cancel()
                                break
                            }
                            last = now
                        }
                    }
                } else {
                    null
                }
                val outcome = try {
                    if (deferred.await()) DownloadStatus.COMPLETED else DownloadStatus.FAILED
                } catch (e: CancellationException) {
                    ensureActive() // rethrow if the whole queue scope was cancelled, not just this item
                    DownloadStatus.CANCELLED
                } catch (e: Throwable) {
                    DownloadStatus.FAILED
                } finally {
                    watchdog?.cancel()
                    activeDownload = null
                }
                // A canceller or the watchdog may have already moved this off DOWNLOADING to a
                // terminal status; only apply the natural outcome if nothing pre-empted it.
                updateItem(next.id) { cur ->
                    if (cur.status == DownloadStatus.DOWNLOADING) cur.copy(status = outcome) else cur
                }
            }
        } finally {
            onIdle()
        }
    }

    private fun setStatus(id: String, status: DownloadStatus) = updateItem(id) { it.copy(status = status) }

    private fun updateItem(id: String, transform: (QueuedDownload) -> QueuedDownload) {
        _queue.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    companion object {
        /** No-progress window before an active download is failed. Generous — real stalls only. */
        const val DEFAULT_STALL_TIMEOUT_MS = 180_000L
    }
}

data class QueueSummary(
    val activeName: String?,
    val position: Int,
    val total: Int,
    val allDone: Boolean,
    /** True only when every item finished successfully (no failures/cancellations). */
    val allSucceeded: Boolean,
)

/** Region names (hex ids) queued or downloading for [layerId] — for highlighting on the map. */
fun List<QueuedDownload>.downloadingHexIds(layerId: String): Set<String> =
    filter { it.layerId == layerId && (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING) }
        .map { it.region.name }
        .toSet()

/** The queue entry for a given hex + layer, matched without rebuilding the [QueuedDownload.id]. */
fun List<QueuedDownload>.forHex(hexId: String, layerId: String): QueuedDownload? =
    find { it.layerId == layerId && it.region.name == hexId }

/** Count-based overview for the "N of M" header; independent of per-item progress. */
fun List<QueuedDownload>.summary(): QueueSummary {
    val finished = count { it.status.isTerminal }
    val active = firstOrNull { it.status == DownloadStatus.DOWNLOADING }
    val hasPending = active != null || any { it.status == DownloadStatus.QUEUED }
    return QueueSummary(
        activeName = active?.label,
        position = finished + if (hasPending) 1 else 0,
        total = size,
        allDone = isNotEmpty() && finished == size,
        allSucceeded = isNotEmpty() && all { it.status == DownloadStatus.COMPLETED },
    )
}
