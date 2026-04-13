package no.synth.where.data

interface OfflineMapDownloadObserver {
    fun onProgress(percent: Int)
    fun onComplete(success: Boolean)
    fun onError(message: String)
}

interface ClearCacheCallback {
    fun onComplete()
}

data class RegionStatus(
    val downloadedTiles: Int,
    val totalTiles: Int,
    val downloadedSize: Long,
    val isComplete: Boolean
)

interface OfflineMapManager {
    fun downloadRegion(
        regionName: String, layerName: String, styleJson: String,
        south: Double, west: Double, north: Double, east: Double,
        minZoom: Int, maxZoom: Int, observer: OfflineMapDownloadObserver
    )
    fun stopDownload(regionName: String)
    fun resumeDownload(regionName: String)

    // Returns null when the region is unknown or progress notifications are still
    // pending; caller should retry or fall back to defaults.
    fun getRegionStatus(regionName: String): RegionStatus?
    fun deleteRegionSync(regionName: String): Boolean
    // Returns null when progress notifications are still pending; caller should retry.
    fun getLayerStats(layerName: String): LayerStats?
    fun getRegionNamesForLayer(layerName: String): List<String>
    fun getDatabaseSize(): Long
    // Clears automatically cached (ambient) tiles; calls callback when complete
    fun clearAmbientCache(callback: ClearCacheCallback)
}
