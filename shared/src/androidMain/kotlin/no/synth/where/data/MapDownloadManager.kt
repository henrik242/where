package no.synth.where.data

import android.content.Context

/**
 * Android offline coverage/stats/deletion facade over [OfflineCoverage] (maplibre-compose's
 * OfflineManager). Downloading itself goes through [MaplibreComposeDownloadEngine] via the
 * [DownloadQueueManager]; this type only reads/deletes what has been downloaded. [context] is
 * retained for call-site compatibility and is unused.
 */
class MapDownloadManager(@Suppress("UNUSED_PARAMETER") context: Context) {

    suspend fun getRegionTileInfo(
        region: Region,
        layerName: String = "kartverket",
        minZoom: Int = 5,
        maxZoom: Int = UserPreferences.DEFAULT_DOWNLOAD_MAX_ZOOM
    ): RegionTileInfo =
        OfflineCoverage.regionTileInfo(
            region.name,
            layerName,
            TileUtils.estimateTileCount(region.boundingBox, minZoom, maxZoom),
        )

    suspend fun deleteRegionTiles(region: Region, layerName: String = "kartverket"): Boolean =
        OfflineCoverage.deleteRegion(region.name, layerName)

    suspend fun getDownloadedRegionsForLayer(layerName: String): Map<String, RegionTileInfo> =
        OfflineCoverage.regionInfosForLayer(layerName)

    suspend fun deleteAllRegionsForLayer(layerName: String): Boolean =
        OfflineCoverage.deleteAllForLayer(layerName)

    suspend fun hasOtherLayersForRegion(regionHexId: String, excludeLayer: String): Boolean =
        OfflineCoverage.hasOtherLayers(regionHexId, excludeLayer)

    suspend fun clearAutoCache(): Boolean {
        OfflineCoverage.clearAmbientCache()
        return true
    }

    suspend fun getLayerStats(layerName: String): LayerStats =
        OfflineCoverage.layerStats(layerName)

    fun getCacheSize(): Long = OfflineCoverage.cacheSize()
}
