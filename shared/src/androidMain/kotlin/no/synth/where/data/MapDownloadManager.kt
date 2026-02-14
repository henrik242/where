package no.synth.where.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.geo.toMapLibre
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import no.synth.where.util.Logger
import kotlin.coroutines.resume

class MapDownloadManager(private val context: Context) {

    companion object {
        private const val STYLE_SERVER_PORT = 8765
    }

    private val offlineManager by lazy { OfflineManager.getInstance(context) }
    private var styleServer: StyleServer? = null
    private val activeDownloads = mutableMapOf<String, OfflineRegion>()

    init {
        startStyleServer()
    }

    private fun startStyleServer() {
        try {
            styleServer = StyleServer.getInstance()
            styleServer?.start()
            Logger.d("Style server started on port %d", STYLE_SERVER_PORT)
        } catch (e: Exception) {
            Logger.e(e, "Failed to start style server")
        }
    }

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
                Logger.d("Starting offline download for: %s on layer %s", region.name, layerName)

                val styleUrl = getStyleUrlForLayer(layerName)
                val regionName = "${region.name}-$layerName"

                val existingRegion = findOfflineRegion(regionName)
                if (existingRegion != null) {
                    Logger.d("Region %s already exists, updating...", regionName)
                    existingRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            Logger.d("Deleted existing region")
                        }

                        override fun onError(error: String) {
                            Logger.w("Error deleting existing region: %s", error)
                        }
                    })
                }

                val definition = OfflineTilePyramidRegionDefinition(
                    styleUrl,
                    region.boundingBox.toMapLibre(),
                    minZoom.toDouble(),
                    maxZoom.toDouble(),
                    context.resources.displayMetrics.density
                )

                val metadata = buildJsonObject {
                    put("name", regionName)
                    put("layer", layerName)
                    put("region", region.name)
                }.toString().toByteArray()

                offlineManager.createOfflineRegion(
                    definition,
                    metadata,
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(offlineRegion: OfflineRegion) {
                            activeDownloads[regionName] = offlineRegion
                            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)

                            offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                                override fun onStatusChanged(status: OfflineRegionStatus) {
                                    val progress = if (status.requiredResourceCount > 0) {
                                        (status.completedResourceCount * 100 / status.requiredResourceCount).toInt()
                                    } else 0

                                    onProgress(progress)

                                    if (status.isComplete) {
                                        offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                        activeDownloads.remove(regionName)
                                        Logger.d("Download complete for %s", regionName)
                                        onComplete(true)
                                    }
                                }

                                override fun onError(error: OfflineRegionError) {
                                    val errorMessage = error.message
                                    val reason = error.reason

                                    val isTemporaryError =
                                        errorMessage.contains("timeout", ignoreCase = true) ||
                                                errorMessage.contains(
                                                    "temporary",
                                                    ignoreCase = true
                                                ) ||
                                                reason.contains("CONNECTION", ignoreCase = true) ||
                                                reason.contains("TIMEOUT", ignoreCase = true)

                                    if (isTemporaryError) {
                                        Logger.w(
                                            "Temporary download error for %s: %s (reason: %s). Download will continue with retry.",
                                            regionName,
                                            errorMessage,
                                            reason
                                        )
                                    } else {
                                        Logger.e(
                                            "Permanent download error for %s: %s (reason: %s)",
                                            regionName,
                                            errorMessage,
                                            reason
                                        )
                                        offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                        activeDownloads.remove(regionName)
                                        onComplete(false)
                                    }
                                }

                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    Logger.w("Tile count limit exceeded: %d", limit)
                                }
                            })
                        }

                        override fun onError(error: String) {
                            Logger.e("Error creating offline region: %s", error)
                            onComplete(false)
                        }
                    })

            } catch (e: Exception) {
                Logger.e(e, "Download error")
                onComplete(false)
            }
        }
    }

    private suspend fun findOfflineRegion(name: String): OfflineRegion? =
        suspendCancellableCoroutine { continuation ->
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val region = offlineRegions?.find { region ->
                        try {
                            val metadata = String(region.metadata)
                            val json = Json.parseToJsonElement(metadata).jsonObject
                            json["name"]?.jsonPrimitive?.content == name
                        } catch (_: Exception) {
                            false
                        }
                    }
                    continuation.resume(region)
                }

                override fun onError(error: String) {
                    Logger.e("Error listing regions: %s", error)
                    continuation.resume(null)
                }
            })
        }

    private fun getStyleUrlForLayer(layerName: String): String {
        return "http://127.0.0.1:$STYLE_SERVER_PORT/styles/$layerName-style.json"
    }

    fun stopDownload(regionName: String) {
        activeDownloads[regionName]?.let { offlineRegion ->
            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
            activeDownloads.remove(regionName)
            Logger.d("Stopped download for %s", regionName)
        }
    }

    suspend fun getRegionTileInfo(
        region: Region,
        layerName: String = "kartverket",
        minZoom: Int = 5,
        maxZoom: Int = 12
    ): RegionTileInfo = suspendCancellableCoroutine { continuation ->
        val regionName = "${region.name}-$layerName"

        val estimatedTileCount = estimateTileCount(region.boundingBox, minZoom, maxZoom)

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val offlineRegion = offlineRegions?.find { r ->
                    try {
                        val metadata = String(r.metadata)
                        val json = Json.parseToJsonElement(metadata).jsonObject
                        json["name"]?.jsonPrimitive?.content == regionName
                    } catch (_: Exception) {
                        false
                    }
                }

                if (offlineRegion != null) {
                    offlineRegion.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status != null) {
                                continuation.resume(
                                    RegionTileInfo(
                                        totalTiles = status.requiredResourceCount.toInt(),
                                        downloadedTiles = status.completedResourceCount.toInt(),
                                        downloadedSize = status.completedResourceSize,
                                        isFullyDownloaded = status.isComplete
                                    )
                                )
                            } else {
                                continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
                            }
                        }

                        override fun onError(error: String?) {
                            continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
                        }
                    })
                } else {
                    continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
                }
            }

            override fun onError(error: String) {
                continuation.resume(RegionTileInfo(estimatedTileCount, 0, 0, false))
            }
        })
    }

    private fun estimateTileCount(bounds: LatLngBounds, minZoom: Int, maxZoom: Int): Int {
        var totalTiles = 0
        for (zoom in minZoom..maxZoom) {
            val tilesPerSide = 1 shl zoom
            val latSpan = bounds.latitudeSpan
            val lonSpan = bounds.longitudeSpan

            val tilesAtZoom =
                ((latSpan / 180.0) * (lonSpan / 360.0) * tilesPerSide * tilesPerSide).toInt()
            totalTiles += tilesAtZoom
        }
        return totalTiles.coerceAtLeast(1)
    }

    suspend fun deleteRegionTiles(region: Region, layerName: String = "kartverket"): Boolean =
        suspendCancellableCoroutine { continuation ->
            val regionName = "${region.name}-$layerName"

            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val offlineRegion = offlineRegions?.find { r ->
                        try {
                            val metadata = String(r.metadata)
                            val json = Json.parseToJsonElement(metadata).jsonObject
                            json["name"]?.jsonPrimitive?.content == regionName
                        } catch (_: Exception) {
                            false
                        }
                    }

                    if (offlineRegion != null) {
                        offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                Logger.d("Deleted region: %s", regionName)
                                continuation.resume(true)
                            }

                            override fun onError(error: String) {
                                Logger.e("Error deleting region: %s", error)
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

    suspend fun getLayerStats(layerName: String): Pair<Long, Int> =
        suspendCancellableCoroutine { continuation ->
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    var totalSize = 0L
                    var totalTiles = 0
                    var processed = 0

                    if (offlineRegions.isNullOrEmpty()) {
                        continuation.resume(Pair(0L, 0))
                        return
                    }

                    val layerRegions = offlineRegions.filter { r ->
                        try {
                            val metadata = String(r.metadata)
                            val json = Json.parseToJsonElement(metadata).jsonObject
                            json["layer"]?.jsonPrimitive?.content == layerName
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
                        })
                    }
                }

                override fun onError(error: String) {
                    continuation.resume(Pair(0L, 0))
                }
            })
        }
}
