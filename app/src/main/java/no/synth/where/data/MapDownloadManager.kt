package no.synth.where.data

import android.content.Context
import android.util.Log
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
    }

    private val offlineManager by lazy { OfflineManager.getInstance(context) }

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
        // OfflineManager requires a full MapLibre style JSON, not just a tile URL
        // We'll create a minimal style JSON as a data URI
        val tileUrl = when (layerName) {
            "kartverket" -> "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"
            "toporaster" -> "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png"
            "sjokartraster" -> "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png"
            "osm" -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            "opentopomap" -> "https://tile.opentopomap.org/{z}/{x}/{y}.png"
            "waymarkedtrails" -> "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"
            else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        }

        // Create a minimal MapLibre style JSON
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

        // Return as data URI
        return "data:application/json;charset=utf-8," + java.net.URLEncoder.encode(styleJson, "UTF-8")
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

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val offlineRegion = offlineRegions?.find { region ->
                    try {
                        val metadata = String(region.metadata)
                        val json = JSONObject(metadata)
                        json.getString("name") == regionName
                    } catch (e: Exception) {
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
                                continuation.resume(RegionTileInfo(0, 0, 0, false))
                            }
                        }

                        override fun onError(error: String?) {
                            continuation.resume(RegionTileInfo(0, 0, 0, false))
                        }

                        @JvmName("onErrorNonNull")
                        fun onError(error: String) {
                            onError(error as String?)
                        }
                    })
                } else {
                    continuation.resume(RegionTileInfo(0, 0, 0, false))
                }
            }

            override fun onError(error: String) {
                continuation.resume(RegionTileInfo(0, 0, 0, false))
            }
        })
    }

    /**
     * Delete all tiles for a specific region
     */
    suspend fun deleteRegionTiles(region: Region, layerName: String = "kartverket"): Boolean = suspendCoroutine { continuation ->
        val regionName = "${region.name}-$layerName"

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val offlineRegion = offlineRegions?.find { region ->
                    try {
                        val metadata = String(region.metadata)
                        val json = JSONObject(metadata)
                        json.getString("name") == regionName
                    } catch (e: Exception) {
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

    data class RegionTileInfo(
        val totalTiles: Int,
        val downloadedTiles: Int,
        val downloadedSize: Long,
        val isFullyDownloaded: Boolean
    )
}
