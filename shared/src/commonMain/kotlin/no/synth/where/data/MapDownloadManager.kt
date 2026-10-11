package no.synth.where.data

import no.synth.where.data.routing.OfflineTerrain
import kotlinx.coroutines.flow.StateFlow

/**
 * Offline map facade shared by all platforms: the download queue plus coverage, stats and deletion
 * over [OfflineCoverage]. Each platform builds the [DownloadQueueManager] with its own engine and
 * scope (Android also starts its foreground service from it).
 */
class MapDownloadManager(private val queueManager: DownloadQueueManager) {

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

    /** Deletes every downloaded region of [layerName], and the DEM tiles no other layer still covers. */
    suspend fun deleteLayer(layerName: String) {
        val downloadedHexIds = getDownloadedRegionsForLayer(layerName)
        deleteAllRegionsForLayer(layerName)
        for (hexId in downloadedHexIds) {
            if (!hasOtherLayersForRegion(hexId, layerName)) {
                HexGrid.hexFromId(hexId)?.let { OfflineTerrain.delete(HexGrid.hexBounds(it)) }
            }
        }
    }

    suspend fun clearAutoCache(): Boolean {
        OfflineCoverage.clearAmbientCache()
        return true
    }

    suspend fun getDownloadedRegionsForLayer(layerName: String): Set<String> =
        OfflineCoverage.downloadedRegionsForLayer(layerName)

    suspend fun getLayerStats(layerName: String): LayerStats =
        OfflineCoverage.layerStats(layerName)
}
