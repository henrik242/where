package no.synth.where.data

interface OfflineMapDownloadObserver {
    fun onProgress(percent: Int)
    fun onComplete(success: Boolean)
    fun onError(message: String)
}

interface ClearCacheCallback {
    fun onComplete()
}

interface OfflineMapManager {
    fun downloadRegion(
        regionName: String, layerName: String, styleJson: String,
        south: Double, west: Double, north: Double, east: Double,
        minZoom: Int, maxZoom: Int, observer: OfflineMapDownloadObserver
    )
    fun stopDownload(regionName: String)
    fun resumeDownload(regionName: String)

    // Synchronous queries — return encoded strings for reliable Kotlin/Native ↔ Swift interop.
    // Returns "downloadedTiles,totalTiles,downloadedSize,isComplete" or "" if not found
    fun getRegionStatusEncoded(regionName: String): String
    fun deleteRegionSync(regionName: String): Boolean
    // Returns "totalSize,totalTiles"
    fun getLayerStatsEncoded(layerName: String): String
    // Returns JSON array of region names for the given layer, e.g. ["hex_5_10-kartverket",...]
    fun getRegionNamesForLayer(layerName: String): String
    fun getDatabaseSize(): Long
    // Clears automatically cached (ambient) tiles; calls callback when complete
    fun clearAmbientCache(callback: ClearCacheCallback)
}
