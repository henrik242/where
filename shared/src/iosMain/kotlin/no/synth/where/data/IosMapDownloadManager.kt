package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import no.synth.where.util.Logger
import kotlin.coroutines.resume

class IosMapDownloadManager(private val offlineMapManager: OfflineMapManager) {

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var pollJob: Job? = null

    fun startDownload(
        region: Region,
        layerName: String,
        minZoom: Int = 5,
        maxZoom: Int = 12
    ) {
        val regionName = "${region.name}-$layerName"
        val styleJson = DownloadLayers.getDownloadStyleJson(layerName)
        Logger.d("startDownload: %s", regionName)

        _downloadState.value = DownloadState(
            region = region,
            layerName = layerName,
            progress = 0,
            isDownloading = true
        )

        offlineMapManager.downloadRegion(
            regionName = regionName,
            layerName = layerName,
            styleJson = styleJson,
            south = region.boundingBox.south,
            west = region.boundingBox.west,
            north = region.boundingBox.north,
            east = region.boundingBox.east,
            minZoom = minZoom,
            maxZoom = maxZoom,
            observer = object : OfflineMapDownloadObserver {
                override fun onProgress(percent: Int) {
                    Logger.d("onProgress callback: %s%%", percent.toString())
                    _downloadState.value = _downloadState.value.copy(progress = percent)
                }

                override fun onComplete(success: Boolean) {
                    Logger.d("onComplete callback for %s: success=%s", regionName, success.toString())
                    pollJob?.cancel()
                    _downloadState.value = DownloadState()
                }

                override fun onError(message: String) {
                    Logger.e("onError callback for %s: %s", regionName, message)
                    pollJob?.cancel()
                    _downloadState.value = DownloadState()
                }
            }
        )

        // Poll progress as fallback — notifications may not reliably call back to Kotlin
        pollJob?.cancel()
        pollJob = scope.launch {
            delay(2000) // Wait for addPack to complete
            Logger.d("Starting progress polling for %s", regionName)
            var lastPolledProgress = -1
            var stallCount = 0
            var stallThreshold = 15 // First stall triggers after ~15s, then backs off
            while (isActive && _downloadState.value.isDownloading) {
                try {
                    val status = offlineMapManager.getRegionStatusEncoded(regionName)
                    Logger.d("Poll status for %s: '%s'", regionName, status)
                    if (status.isNotEmpty()) {
                        val parts = status.split(",")
                        val downloaded = parts[0].toIntOrNull() ?: 0
                        val total = parts[1].toIntOrNull() ?: 1
                        val isComplete = parts.getOrNull(3) == "true"
                        if (total > 0) {
                            val percent = (downloaded * 100 / total).coerceIn(0, 100)
                            _downloadState.value = _downloadState.value.copy(progress = percent)
                        }
                        if (isComplete) {
                            Logger.d("Polling detected completion for %s", regionName)
                            _downloadState.value = DownloadState()
                            break
                        }
                        // Stall detection: auto-resume when no tile progress
                        if (downloaded == lastPolledProgress && downloaded > 0) {
                            stallCount++
                            if (stallCount >= stallThreshold) {
                                Logger.d("Auto-resuming stalled pack for %s (stuck at %s tiles for %ss)", regionName, downloaded.toString(), stallCount.toString())
                                offlineMapManager.resumeDownload(regionName)
                                stallCount = 0
                                // Keep lastPolledProgress — don't reset to -1 or the
                                // "progress changed" branch will reset stallThreshold
                                stallThreshold = (stallThreshold * 2).coerceAtMost(120)
                                delay(5_000) // Cooldown: let server throttle expire
                                continue
                            }
                        } else if (downloaded > lastPolledProgress) {
                            stallCount = 0
                            stallThreshold = 15 // Reset backoff on real progress
                        }
                        lastPolledProgress = downloaded
                    }
                } catch (e: Exception) {
                    Logger.e("Poll error for %s: %s", regionName, e.message ?: "unknown")
                }
                delay(1000)
            }
            Logger.d("Stopped polling for %s", regionName)
        }
    }

    fun stopDownload() {
        val state = _downloadState.value
        if (state.isDownloading && state.region != null && state.layerName != null) {
            val regionName = "${state.region.name}-${state.layerName}"
            Logger.d("stopDownload: %s", regionName)
            offlineMapManager.stopDownload(regionName)
        }
        pollJob?.cancel()
        _downloadState.value = DownloadState()
    }

    suspend fun getRegionTileInfo(
        region: Region,
        layerName: String,
        minZoom: Int = 5,
        maxZoom: Int = 12
    ): RegionTileInfo {
        val regionName = "${region.name}-$layerName"
        val estimatedTileCount = TileUtils.estimateTileCount(region.boundingBox, minZoom, maxZoom)
        return try {
            var result = offlineMapManager.getRegionStatusEncoded(regionName)
            // Sentinel: state=Unknown after reloadPacks() — wait for requestProgress() notification
            var retries = 0
            while (result == "-1,-1,-1,-1" && retries < 5) {
                delay(300)
                result = offlineMapManager.getRegionStatusEncoded(regionName)
                retries++
            }
            Logger.d("getRegionTileInfo %s: estimated=%s, status='%s'", regionName, estimatedTileCount.toString(), result)
            if (result.isEmpty() || result == "-1,-1,-1,-1") {
                return RegionTileInfo(estimatedTileCount, 0, 0, false)
            }
            val parts = result.split(",")
            val downloaded = parts[0].toIntOrNull() ?: 0
            val total = parts[1].toIntOrNull() ?: 0
            val size = parts[2].toLongOrNull() ?: 0L
            val isComplete = parts.getOrNull(3) == "true"
            // Pack state is complete but tile count is stale (0) — wait for the
            // requestProgress() notification to populate cachedStatus, then retry
            // to get the accurate count. isComplete is already correct so ✓ shows.
            if (isComplete && downloaded == 0) {
                delay(300)
                val retry = offlineMapManager.getRegionStatusEncoded(regionName)
                if (!retry.isEmpty() && retry != "-1,-1,-1,-1") {
                    val rp = retry.split(",")
                    return RegionTileInfo(
                        totalTiles = rp.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: estimatedTileCount,
                        downloadedTiles = rp.getOrNull(0)?.toIntOrNull() ?: 0,
                        downloadedSize = rp.getOrNull(2)?.toLongOrNull() ?: 0L,
                        isFullyDownloaded = rp.getOrNull(3) == "true"
                    )
                }
            }
            RegionTileInfo(
                totalTiles = if (total > 0) total else estimatedTileCount,
                downloadedTiles = downloaded,
                downloadedSize = size,
                isFullyDownloaded = isComplete
            )
        } catch (e: Exception) {
            Logger.e("getRegionTileInfo error for %s: %s", regionName, e.message ?: "unknown")
            RegionTileInfo(estimatedTileCount, 0, 0, false)
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

    suspend fun getAmbientCacheSize(): Long {
        val totalSize = offlineMapManager.getDatabaseSize()
        val packsSize = DownloadLayers.all.sumOf { layer -> getLayerStats(layer.id).first }
        return maxOf(0L, totalSize - packsSize)
    }

    suspend fun deleteAllRegionsForLayer(layerName: String): Boolean {
        return try {
            val json = offlineMapManager.getRegionNamesForLayer(layerName)
            if (json == "[]" || json.isBlank()) return true
            json.removeSurrounding("[", "]")
                .split(",")
                .mapNotNull { s -> s.trim().removeSurrounding("\"").takeIf { it.isNotBlank() } }
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
            val json = offlineMapManager.getRegionNamesForLayer(layerName)
            if (json == "[]" || json.isBlank()) return emptySet()
            val suffix = "-$layerName"
            json.removeSurrounding("[", "]")
                .split(",")
                .mapNotNull { s ->
                    val name = s.trim().removeSurrounding("\"")
                    if (name.endsWith(suffix)) name.dropLast(suffix.length) else null
                }
                .toSet()
        } catch (e: Exception) {
            Logger.e("getDownloadedRegionsForLayer error for %s: %s", layerName, e.message ?: "unknown")
            emptySet()
        }
    }

    suspend fun getLayerStats(layerName: String): Pair<Long, Int> {
        return try {
            var result = offlineMapManager.getLayerStatsEncoded(layerName)
            if (result == "-1,-1") {
                // requestProgress() was triggered for stale packs; wait for notifications
                delay(300)
                result = offlineMapManager.getLayerStatsEncoded(layerName)
            }
            Logger.d("getLayerStats %s: '%s'", layerName, result)
            if (result.isEmpty() || result == "-1,-1") Pair(0L, 0)
            else {
                val parts = result.split(",")
                Pair(
                    parts[0].toLongOrNull() ?: 0L,
                    parts[1].toIntOrNull() ?: 0
                )
            }
        } catch (e: Exception) {
            Logger.e("getLayerStats error for %s: %s", layerName, e.message ?: "unknown")
            Pair(0L, 0)
        }
    }
}
