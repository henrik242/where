package no.synth.where.data

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MapDownloadManager(private val context: Context) {

    companion object {
        const val DEFAULT_MAX_CACHE_SIZE_MB = 500L
        private const val STYLE_SERVER_PORT = 8765
    }

    private val offlineManager by lazy { OfflineManager.getInstance(context) }
    private var styleServer: StyleServer? = null

    init {
        // Start the local HTTP server for serving style JSON files
        startStyleServer()
    }

    private fun startStyleServer() {
        try {
            styleServer = StyleServer(STYLE_SERVER_PORT)
            styleServer?.start()
            Log.d("MapDownloadManager", "Style server started on port $STYLE_SERVER_PORT")
        } catch (e: Exception) {
            Log.e("MapDownloadManager", "Failed to start style server", e)
        }
    }

    private class StyleServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri

            // Extract layer name from URI like /styles/kartverket-style.json
            val layerName = uri.substringAfter("/styles/").substringBefore("-style.json")

            val tileUrl = when (layerName) {
                "kartverket" -> "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"
                "toporaster" -> "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png"
                "sjokartraster" -> "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png"
                "osm" -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                "opentopomap" -> "https://tile.opentopomap.org/{z}/{x}/{y}.png"
                "waymarkedtrails" -> "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"
                else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            }

            val styleJson = """
{
  "version": 8,
  "sources": {
    "$layerName": {
      "type": "raster",
      "tiles": ["$tileUrl"],
      "tileSize": 256
    }
  },
  "layers": [
    {
      "id": "$layerName-layer",
      "type": "raster",
      "source": "$layerName"
    }
  ]
}
            """.trimIndent()

            return newFixedLengthResponse(Response.Status.OK, "application/json", styleJson)
        }
    }


    /**
     * Download map tiles for a region using MapLibre's OfflineManager
     */
    suspend fun downloadRegion(
        region: Region,
        layerName: String = "kartverket",
        minZoom: Int = 5,
        maxZoom: Int = 15,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            try {
                Log.d("MapDownloadManager", "Starting offline download for: ${region.name} on layer $layerName")

                val styleUrl = getStyleUrlForLayer(layerName)
                val regionName = "${region.name}-$layerName"

                // Check if region already exists
                val existingRegion = findOfflineRegion(regionName)
                if (existingRegion != null) {
                    Log.d("MapDownloadManager", "Region $regionName already exists, updating...")
                    existingRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            Log.d("MapDownloadManager", "Deleted existing region")
                        }
                        override fun onError(error: String) {
                            Log.w("MapDownloadManager", "Error deleting existing region: $error")
                        }
                    })
                }

                // Create offline region definition
                val definition = OfflineTilePyramidRegionDefinition(
                    styleUrl,
                    region.boundingBox,
                    minZoom.toDouble(),
                    maxZoom.toDouble(),
                    context.resources.displayMetrics.density
                )

                val metadata = JSONObject().apply {
                    put("name", regionName)
                    put("layer", layerName)
                    put("region", region.name)
                }.toString().toByteArray()

                // Create the offline region
                offlineManager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)

                        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                val progress = if (status.requiredResourceCount > 0) {
                                    (status.completedResourceCount * 100 / status.requiredResourceCount).toInt()
                                } else 0

                                onProgress(progress)

                                if (status.isComplete) {
                                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    Log.d("MapDownloadManager", "Download complete for $regionName")
                                    onComplete(true)
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                Log.e("MapDownloadManager", "Download error: ${error.message}")
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                onComplete(false)
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                Log.w("MapDownloadManager", "Tile count limit exceeded: $limit")
                            }
                        })
                    }

                    override fun onError(error: String) {
                        Log.e("MapDownloadManager", "Error creating offline region: $error")
                        onComplete(false)
                    }
                })

            } catch (e: Exception) {
                Log.e("MapDownloadManager", "Download error", e)
                onComplete(false)
            }
        }
    }

    private suspend fun findOfflineRegion(name: String): OfflineRegion? = suspendCoroutine { continuation ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val region = offlineRegions?.find { region ->
                    try {
                        val metadata = String(region.metadata)
                        val json = JSONObject(metadata)
                        json.getString("name") == name
                    } catch (e: Exception) {
                        false
                    }
                }
                continuation.resume(region)
            }

            override fun onError(error: String) {
                Log.e("MapDownloadManager", "Error listing regions: $error")
                continuation.resume(null)
            }
        })
    }

    private fun getStyleUrlForLayer(layerName: String): String {
        // Return the localhost URL for the style JSON served by our local HTTP server
        return "http://127.0.0.1:$STYLE_SERVER_PORT/styles/$layerName-style.json"
    }

    /**
     * Get info about downloaded regions
     */
    suspend fun getRegionTileInfo(
        region: Region,
        layerName: String = "kartverket",
        minZoom: Int = 5,
        maxZoom: Int = 12
    ): RegionTileInfo = suspendCoroutine { continuation ->
        val regionName = "${region.name}-$layerName"

        // Calculate estimated tile count for this region
        val estimatedTileCount = estimateTileCount(region.boundingBox, minZoom, maxZoom)

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val offlineRegion = offlineRegions?.find { r ->
                    try {
                        val metadata = String(r.metadata)
                        val json = JSONObject(metadata)
                        json.getString("name") == regionName
                    } catch (_: Exception) {
                        false
                    }
                }

                if (offlineRegion != null) {
                    offlineRegion.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status != null) {
                                continuation.resume(RegionTileInfo(
                                    totalTiles = status.requiredResourceCount.toInt(),
                                    downloadedTiles = status.completedResourceCount.toInt(),
                                    downloadedSize = status.completedResourceSize,
                                    isFullyDownloaded = status.isComplete
                                ))
                            } else {
                                continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
                            }
                        }

                        override fun onError(error: String?) {
                            continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
                        }

                        @JvmName("onErrorNonNull")
                        fun onError(error: String) {
                            onError(error as String?)
                        }
                    })
                } else {
                    // Region not downloaded yet, return estimated tile count
                    continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
                }
            }

            override fun onError(error: String) {
                continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
            }
        })
    }

    /**
     * Estimate the number of tiles needed for a bounding box across zoom levels
     */
    private fun estimateTileCount(bounds: LatLngBounds, minZoom: Int, maxZoom: Int): Int {
        var totalTiles = 0
        for (zoom in minZoom..maxZoom) {
            val tilesPerSide = 1 shl zoom // 2^zoom
            val latSpan = bounds.latitudeSpan
            val lonSpan = bounds.longitudeSpan

            // Rough estimate: tiles = (latSpan/180) * (lonSpan/360) * tilesPerSide^2
            val tilesAtZoom = ((latSpan / 180.0) * (lonSpan / 360.0) * tilesPerSide * tilesPerSide).toInt()
            totalTiles += tilesAtZoom
        }
        return totalTiles.coerceAtLeast(1)
    }

    /**
     * Delete all tiles for a specific region
     */
    suspend fun deleteRegionTiles(region: Region, layerName: String = "kartverket"): Boolean = suspendCoroutine { continuation ->
        val regionName = "${region.name}-$layerName"

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val offlineRegion = offlineRegions?.find { r ->
                    try {
                        val metadata = String(r.metadata)
                        val json = JSONObject(metadata)
                        json.getString("name") == regionName
                    } catch (_: Exception) {
                        false
                    }
                }

                if (offlineRegion != null) {
                    offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            Log.d("MapDownloadManager", "Deleted region: $regionName")
                            continuation.resume(true)
                        }

                        override fun onError(error: String) {
                            Log.e("MapDownloadManager", "Error deleting region: $error")
                            continuation.resume(false)
                        }
                    })
                } else {
                    continuation.resume(false)
                }
            }

            override fun onError(error: String) {
                continuation.resume(false)
            }
        })
    }

    /**
     * Get total cache size
     */
    suspend fun getTotalCacheInfo(): Pair<Long, Int> = suspendCoroutine { continuation ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                var totalSize = 0L
                var totalTiles = 0
                var processed = 0

                if (offlineRegions.isNullOrEmpty()) {
                    continuation.resume(Pair(0L, 0))
                    return
                }

                offlineRegions.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status != null) {
                                totalSize += status.completedResourceSize
                                totalTiles += status.completedResourceCount.toInt()
                            }
                            processed++

                            if (processed == offlineRegions.size) {
                                continuation.resume(Pair(totalSize, totalTiles))
                            }
                        }

                        override fun onError(error: String?) {
                            processed++
                            if (processed == offlineRegions.size) {
                                continuation.resume(Pair(totalSize, totalTiles))
                            }
                        }

                        @JvmName("onErrorNonNull")
                        fun onError(error: String) {
                            onError(error as String?)
                        }
                    })
                }
            }

            override fun onError(error: String) {
                continuation.resume(Pair(0L, 0))
            }
        })
    }

    /**
     * Get statistics for a specific layer
     */
    suspend fun getLayerStats(layerName: String): Pair<Long, Int> = suspendCoroutine { continuation ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                var totalSize = 0L
                var totalTiles = 0
                var processed = 0

                if (offlineRegions.isNullOrEmpty()) {
                    continuation.resume(Pair(0L, 0))
                    return
                }

                // Filter regions for this specific layer
                val layerRegions = offlineRegions.filter { r ->
                    try {
                        val metadata = String(r.metadata)
                        val json = JSONObject(metadata)
                        json.getString("layer") == layerName
                    } catch (_: Exception) {
                        false
                    }
                }

                if (layerRegions.isEmpty()) {
                    continuation.resume(Pair(0L, 0))
                    return
                }

                layerRegions.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status != null) {
                                totalSize += status.completedResourceSize
                                totalTiles += status.completedResourceCount.toInt()
                            }
                            processed++

                            if (processed == layerRegions.size) {
                                continuation.resume(Pair(totalSize, totalTiles))
                            }
                        }

                        override fun onError(error: String?) {
                            processed++
                            if (processed == layerRegions.size) {
                                continuation.resume(Pair(totalSize, totalTiles))
                            }
                        }

                        @JvmName("onErrorNonNull")
                        fun onError(error: String) {
                            onError(error as String?)
                        }
                    })
                }
            }

            override fun onError(error: String) {
                continuation.resume(Pair(0L, 0))
            }
        })
    }

    data class RegionTileInfo(
        val totalTiles: Int,
        val downloadedTiles: Int,
        val downloadedSize: Long,
        val isFullyDownloaded: Boolean
    )
}
