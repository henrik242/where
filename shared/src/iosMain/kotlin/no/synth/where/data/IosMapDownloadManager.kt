package no.synth.where.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import no.synth.where.util.Logger
import kotlin.coroutines.resume

/**
 * iOS download manager. Owns the shared [DownloadQueueManager] and is itself the
 * [DownloadEngine] that runs one region (native tiles via [OfflineMapManager] + DEM) to a
 * terminal state. The old single-download API is gone — everything goes through the queue.
 */
class IosMapDownloadManager(private val offlineMapManager: OfflineMapManager) : DownloadEngine {

    // Main-confined + SupervisorJob to match Android and honour DownloadQueueManager's scope
    // contract (a stray throw must not permanently cancel the queue's scope).
    private val queueManager = DownloadQueueManager(
        engine = this,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    val queue: StateFlow<List<QueuedDownload>> get() = queueManager.queue
    fun enqueue(item: QueuedDownload) = queueManager.enqueue(item)
    fun cancel(id: String) = queueManager.cancel(id)
    fun clearFinished() = queueManager.clearFinished()

    private var activeRegionId: String? = null

    override suspend fun download(
        item: QueuedDownload,
        onProgress: (mapPercent: Int, demPercent: Int) -> Unit,
    ): Boolean = coroutineScope {
        activeRegionId = item.id
        var mapPercent = 0
        var demPercent = if (item.downloadDem) 0 else -1

        val demJob = if (item.downloadDem) {
            async {
                OfflineTileReader.downloadDemTilesForBounds(item.region.boundingBox) { percent ->
                    demPercent = percent
                    onProgress(mapPercent, demPercent)
                }
            }
        } else {
            null
        }

        val mapResult = CompletableDeferred<Boolean>()
        val bounds = item.region.boundingBox
        offlineMapManager.downloadRegion(
            regionName = item.id,
            layerName = item.layerId,
            styleJson = DownloadLayers.getDownloadStyleJson(item.layerId),
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            minZoom = item.minZoom,
            maxZoom = item.maxZoom,
            observer = object : OfflineMapDownloadObserver {
                override fun onProgress(percent: Int) {
                    mapPercent = percent
                    onProgress(mapPercent, demPercent)
                }

                override fun onComplete(success: Boolean) {
                    mapResult.complete(success) // idempotent; poller may also complete
                }

                override fun onError(message: String) {
                    Logger.e("download error for %s: %s", item.id, message)
                    mapResult.complete(false)
                }
            },
        )

        // Fallback poll loop: notifications may not reliably call back to Kotlin, and it
        // auto-resumes packs that stall behind server throttling.
        val poller = launch {
            delay(2000) // wait for addPack to register
            var lastPolled = -1
            var stallCount = 0
            var stallThreshold = 15
            while (isActive && !mapResult.isCompleted) {
                try {
                    val status = offlineMapManager.getRegionStatus(item.id)
                    if (status != null) {
                        val total = if (status.totalTiles > 0) status.totalTiles else 1
                        val percent = (status.downloadedTiles * 100 / total).coerceIn(0, 100)
                        mapPercent = percent
                        onProgress(mapPercent, demPercent)
                        if (status.isComplete) {
                            mapResult.complete(true)
                            break
                        }
                        if (status.downloadedTiles == lastPolled && status.downloadedTiles > 0) {
                            stallCount++
                            if (stallCount >= stallThreshold) {
                                Logger.d("Auto-resuming stalled pack %s", item.id)
                                offlineMapManager.resumeDownload(item.id)
                                stallCount = 0
                                stallThreshold = (stallThreshold * 2).coerceAtMost(120)
                                delay(5_000)
                                continue
                            }
                        } else if (status.downloadedTiles > lastPolled) {
                            stallCount = 0
                            stallThreshold = 15
                        }
                        lastPolled = status.downloadedTiles
                    }
                } catch (e: Exception) {
                    Logger.e("Poll error for %s: %s", item.id, e.message ?: "unknown")
                }
                delay(1000)
            }
        }

        try {
            val mapOk = mapResult.await()
            demJob?.await()
            mapOk
        } finally {
            poller.cancel()
            activeRegionId = null
        }
    }

    override fun cancelActive() {
        activeRegionId?.let { offlineMapManager.stopDownload(it) }
    }

    suspend fun getRegionTileInfo(
        region: Region,
        layerName: String,
        minZoom: Int = 5,
        maxZoom: Int = UserPreferences.DEFAULT_DOWNLOAD_MAX_ZOOM,
    ): RegionTileInfo {
        val regionName = "${region.name}-$layerName"
        val estimatedTileCount = TileUtils.estimateTileCount(region.boundingBox, minZoom, maxZoom)
        return try {
            // Retry on null: state=Unknown after reloadPacks() — wait for requestProgress() notification
            var status = offlineMapManager.getRegionStatus(regionName)
            var retries = 0
            while (status == null && retries < 5) {
                delay(300)
                status = offlineMapManager.getRegionStatus(regionName)
                retries++
            }
            Logger.d("getRegionTileInfo %s: estimated=%s, status=%s", regionName, estimatedTileCount.toString(), status?.toString() ?: "unavailable")
            if (status == null) {
                return RegionTileInfo(estimatedTileCount, 0, 0, false)
            }
            // Pack state is complete but tile count is stale (0) — wait for the
            // requestProgress() notification to populate cachedStatus, then retry
            // to get the accurate count. isComplete is already correct so ✓ shows.
            val resolved = if (status.isComplete && status.downloadedTiles == 0) {
                delay(300)
                offlineMapManager.getRegionStatus(regionName) ?: status
            } else status
            RegionTileInfo(
                totalTiles = if (resolved.totalTiles > 0) resolved.totalTiles else estimatedTileCount,
                downloadedTiles = resolved.downloadedTiles,
                downloadedSize = resolved.downloadedSize,
                isFullyDownloaded = resolved.isComplete
            )
        } catch (e: Exception) {
            Logger.e("getRegionTileInfo error for %s: %s", regionName, e.message ?: "unknown")
            RegionTileInfo(estimatedTileCount, 0, 0, false)
        }
    }

    fun hasOtherLayersForRegion(regionHexId: String, excludeLayer: String): Boolean {
        return try {
            DownloadLayers.all
                .filter { it.id != excludeLayer }
                .any { layer ->
                    val regionName = "$regionHexId-${layer.id}"
                    (offlineMapManager.getRegionStatus(regionName)?.downloadedTiles ?: 0) > 0
                }
        } catch (e: Exception) {
            Logger.e("hasOtherLayersForRegion error: %s", e.message ?: "unknown")
            false
        }
    }

    suspend fun deleteRegionTiles(region: Region, layerName: String): Boolean {
        val regionName = "${region.name}-$layerName"
        return try {
            offlineMapManager.deleteRegionSync(regionName)
        } catch (e: Exception) {
            Logger.e("deleteRegionTiles error for %s: %s", regionName, e.message ?: "unknown")
            false
        }
    }

    fun getCacheSize(): Long = offlineMapManager.getDatabaseSize()

    suspend fun deleteAllRegionsForLayer(layerName: String): Boolean {
        return try {
            offlineMapManager.getRegionNamesForLayer(layerName)
                .forEach { name -> offlineMapManager.deleteRegionSync(name) }
            true
        } catch (e: Exception) {
            Logger.e("deleteAllRegionsForLayer error for %s: %s", layerName, e.message ?: "unknown")
            false
        }
    }

    suspend fun clearAutoCache(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            offlineMapManager.clearAmbientCache(object : ClearCacheCallback {
                override fun onComplete() {
                    continuation.resume(true)
                }
            })
        } catch (e: Exception) {
            Logger.e("clearAutoCache error: %s", e.message ?: "unknown")
            continuation.resume(false)
        }
    }

    suspend fun getDownloadedRegionsForLayer(layerName: String): Set<String> {
        return try {
            val suffix = "-$layerName"
            offlineMapManager.getRegionNamesForLayer(layerName)
                .mapNotNull { name -> if (name.endsWith(suffix)) name.dropLast(suffix.length) else null }
                .toSet()
        } catch (e: Exception) {
            Logger.e("getDownloadedRegionsForLayer error for %s: %s", layerName, e.message ?: "unknown")
            emptySet()
        }
    }

    suspend fun getLayerStats(layerName: String): LayerStats {
        return try {
            // null means requestProgress() was triggered for stale packs; wait for notifications
            val stats = offlineMapManager.getLayerStats(layerName) ?: run {
                delay(300)
                offlineMapManager.getLayerStats(layerName)
            } ?: LayerStats.EMPTY
            Logger.d("getLayerStats %s: %d bytes, %d tiles", layerName, stats.sizeBytes, stats.tileCount)
            stats
        } catch (e: Exception) {
            Logger.e("getLayerStats error for %s: %s", layerName, e.message ?: "unknown")
            LayerStats.EMPTY
        }
    }
}
