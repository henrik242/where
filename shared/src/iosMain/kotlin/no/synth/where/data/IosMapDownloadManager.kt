package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSTemporaryDirectory

/**
 * iOS download facade. Owns the shared [DownloadQueueManager] (map tiles via
 * [MaplibreComposeDownloadEngine] + DEM via [OfflineTileReader]) and exposes coverage/stats/delete
 * over [OfflineCoverage]. The legacy Swift OfflineMapManager is gone.
 */
class IosMapDownloadManager {

    private val queueManager = DownloadQueueManager(
        engine = MaplibreComposeDownloadEngine(PlatformFile(NSTemporaryDirectory())),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    val queue: StateFlow<List<QueuedDownload>> get() = queueManager.queue
    fun enqueue(item: QueuedDownload) = queueManager.enqueue(item)
    fun cancel(id: String) = queueManager.cancel(id)
    fun clearFinished() = queueManager.clearFinished()

    suspend fun getRegionTileInfo(
        region: Region,
        layerName: String,
        minZoom: Int = 5,
        maxZoom: Int = UserPreferences.DEFAULT_DOWNLOAD_MAX_ZOOM,
    ): RegionTileInfo =
        OfflineCoverage.regionTileInfo(
            region.name,
            layerName,
            TileUtils.estimateTileCount(region.boundingBox, minZoom, maxZoom),
        )

    suspend fun hasOtherLayersForRegion(regionHexId: String, excludeLayer: String): Boolean =
        OfflineCoverage.hasOtherLayers(regionHexId, excludeLayer)

    suspend fun deleteRegionTiles(region: Region, layerName: String): Boolean =
        OfflineCoverage.deleteRegion(region.name, layerName)

    fun getCacheSize(): Long = OfflineCoverage.cacheSize()

    suspend fun deleteAllRegionsForLayer(layerName: String): Boolean =
        OfflineCoverage.deleteAllForLayer(layerName)

    suspend fun clearAutoCache(): Boolean {
        OfflineCoverage.clearAmbientCache()
        return true
    }

    suspend fun getDownloadedRegionsForLayer(layerName: String): Set<String> =
        OfflineCoverage.downloadedRegionsForLayer(layerName)

    suspend fun getLayerStats(layerName: String): LayerStats =
        OfflineCoverage.layerStats(layerName)
}
