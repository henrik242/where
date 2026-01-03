package no.synth.where.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

class MapDownloadManager(private val context: Context) {

    companion object {
        private const val TILE_REFRESH_DAYS = 90
        const val DEFAULT_MAX_CACHE_SIZE_MB = 500L

        private val LAYER_URLS = mapOf(
            "kartverket" to "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator",
            "toporaster" to "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator",
            "osm" to "https://tile.openstreetmap.org",
            "opentopomap" to "https://tile.opentopomap.org"
        )

        private val LAYER_COORD_FORMAT = mapOf(
            "kartverket" to "{z}/{y}/{x}",
            "toporaster" to "{z}/{y}/{x}",
            "osm" to "{z}/{x}/{y}",
            "opentopomap" to "{z}/{x}/{y}"
        )
    }

    private fun getTilesDir(layerName: String) = File(context.getExternalFilesDir(null), "tiles/$layerName")
    private fun getMetadataFile(layerName: String) = File(context.getExternalFilesDir(null), "tiles/${layerName}_metadata.json")

    data class TileMetadata(
        val regions: MutableSet<String> = mutableSetOf(),
        var downloadedAt: Long = System.currentTimeMillis()
    )

    private fun loadMetadata(layerName: String): MutableMap<String, TileMetadata> {
        val metadataFile = getMetadataFile(layerName)
        if (!metadataFile.exists()) return mutableMapOf()
        return try {
            val json = metadataFile.readText()
            com.google.gson.Gson().fromJson(json,
                object : com.google.gson.reflect.TypeToken<MutableMap<String, TileMetadata>>() {}.type)
                ?: mutableMapOf()
        } catch (e: Exception) {
            Log.w("MapDownloadManager", "Failed to load metadata for $layerName", e)
            mutableMapOf()
        }
    }

    private fun saveMetadata(layerName: String, metadata: Map<String, TileMetadata>) {
        try {
            val metadataFile = getMetadataFile(layerName)
            metadataFile.parentFile?.mkdirs()
            val json = com.google.gson.Gson().toJson(metadata)
            metadataFile.writeText(json)
        } catch (e: Exception) {
            Log.e("MapDownloadManager", "Failed to save metadata for $layerName", e)
        }
    }

    /**
     * Download map tiles for a region
     */
    suspend fun downloadRegion(
        region: Region,
        layerName: String = "kartverket",
        minZoom: Int = 5,
        maxZoom: Int = 15,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("MapDownloadManager", "Starting tile download for: ${region.name} on layer $layerName")
                Log.d("MapDownloadManager", "Zoom levels: $minZoom - $maxZoom")

                // Create tiles directory for this layer
                val tilesDir = getTilesDir(layerName)
                tilesDir.mkdirs()

                // Load metadata for this layer
                val metadata = loadMetadata(layerName)

                // Calculate tile ranges for each zoom level
                val tilesToDownload = mutableListOf<TileCoordinate>()
                for (zoom in minZoom..maxZoom) {
                    val tiles = getTilesForBounds(region.boundingBox, zoom)
                    tilesToDownload.addAll(tiles)
                }

                Log.d("MapDownloadManager", "Total tiles to download: ${tilesToDownload.size}")

                var downloaded = 0
                var skipped = 0
                var failed = 0
                val currentTime = System.currentTimeMillis()

                for ((index, tile) in tilesToDownload.withIndex()) {
                    val coordFormat = LAYER_COORD_FORMAT[layerName] ?: "{z}/{x}/{y}"
                    val tileKey = if (coordFormat == "{z}/{y}/{x}") {
                        "${tile.z}/${tile.y}/${tile.x}"
                    } else {
                        "${tile.z}/${tile.x}/${tile.y}"
                    }
                    val tileFile = File(tilesDir, "$tileKey.png")
                    val tileMeta = metadata[tileKey]

                    // Skip if tile exists and is not outdated
                    val tileAge = if (tileFile.exists() && tileMeta != null) {
                        (currentTime - tileMeta.downloadedAt) / (1000 * 60 * 60 * 24)
                    } else -1

                    if (tileFile.exists() && tileAge >= 0 && tileAge < TILE_REFRESH_DAYS) {
                        // Update metadata to associate with this region
                        tileMeta?.regions?.add(region.name)
                        skipped++
                    } else {
                        // Download tile (updates existing or creates new)
                        val success = downloadTile(tile, layerName, tilesDir)
                        if (success) {
                            // Update or create metadata
                            val meta = metadata.getOrPut(tileKey) { TileMetadata() }
                            meta.regions.add(region.name)
                            meta.downloadedAt = currentTime
                            downloaded++
                        } else {
                            failed++
                        }
                    }

                    val progress = ((index + 1) * 100) / tilesToDownload.size
                    onProgress(progress)

                    if (index % 10 == 0) {
                        Log.d("MapDownloadManager", "Progress: $progress% ($downloaded new, $skipped existing, $failed failed)")
                    }
                }

                // Save metadata for this layer
                saveMetadata(layerName, metadata)

                Log.d("MapDownloadManager", "Download complete: $downloaded new, $skipped existing, $failed failed")
                onComplete(failed == 0)

            } catch (e: Exception) {
                Log.e("MapDownloadManager", "Download error", e)
                onComplete(false)
            }
        }
    }

    private fun getTilesForBounds(bounds: org.maplibre.android.geometry.LatLngBounds, zoom: Int): List<TileCoordinate> {
        val tiles = mutableListOf<TileCoordinate>()

        val northWest = latLngToTile(bounds.northEast.latitude, bounds.southWest.longitude, zoom)
        val southEast = latLngToTile(bounds.southWest.latitude, bounds.northEast.longitude, zoom)

        val minX = minOf(northWest.x, southEast.x)
        val maxX = maxOf(northWest.x, southEast.x)
        val minY = minOf(northWest.y, southEast.y)
        val maxY = maxOf(northWest.y, southEast.y)

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                tiles.add(TileCoordinate(zoom, x, y))
            }
        }

        return tiles
    }

    private fun latLngToTile(lat: Double, lng: Double, zoom: Int): TileCoordinate {
        val n = 2.0.pow(zoom)
        val x = floor((lng + 180.0) / 360.0 * n).toInt()
        val latRad = lat * PI / 180.0
        val y = floor((1.0 - ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n).toInt()
        return TileCoordinate(zoom, x, y)
    }

    private suspend fun downloadTile(tile: TileCoordinate, layerName: String, tilesDir: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = LAYER_URLS[layerName] ?: return@withContext false
                val coordFormat = LAYER_COORD_FORMAT[layerName] ?: "{z}/{x}/{y}"

                // Build URL based on coordinate format
                val url = if (coordFormat == "{z}/{y}/{x}") {
                    "$baseUrl/${tile.z}/${tile.y}/${tile.x}.png"
                } else {
                    "$baseUrl/${tile.z}/${tile.x}/${tile.y}.png"
                }

                // Build file path (same as URL path)
                val tilePath = if (coordFormat == "{z}/{y}/{x}") {
                    "${tile.z}/${tile.y}/${tile.x}.png"
                } else {
                    "${tile.z}/${tile.x}/${tile.y}.png"
                }
                val tileFile = File(tilesDir, tilePath)

                // Create directory
                tileFile.parentFile?.mkdirs()

                // Download tile (overwrites if exists and outdated)
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Hvor/1.0")

                if (connection.responseCode == 200) {
                    val contentLength = connection.contentLength
                    connection.inputStream.use { input ->
                        FileOutputStream(tileFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val actualSize = tileFile.length()

                    // Log a sample of downloads for debugging
                    if (tile.z <= 7 || (tile.x + tile.y) % 50 == 0) {
                        Log.d("MapDownloadManager", "Downloaded $layerName tile $tilePath: " +
                            "expected=$contentLength bytes, actual=$actualSize bytes")
                    }

                    true
                } else {
                    Log.w("MapDownloadManager", "Failed to download tile: ${connection.responseCode} for $url")
                    false
                }
            } catch (e: Exception) {
                Log.w("MapDownloadManager", "Error downloading tile for $layerName", e)
                false
            }
        }
    }

    /**
     * Get the tiles required for a region and their total size
     */
    fun getRegionTileInfo(
        region: Region,
        layerName: String = "kartverket",
        minZoom: Int = 5,
        maxZoom: Int = 12
    ): RegionTileInfo {
        val metadata = loadMetadata(layerName)
        val tilesDir = getTilesDir(layerName)
        val tilesToCheck = mutableListOf<TileCoordinate>()

        for (zoom in minZoom..maxZoom) {
            val tiles = getTilesForBounds(region.boundingBox, zoom)
            tilesToCheck.addAll(tiles)
        }

        var existingTiles = 0
        var existingSize = 0L

        val coordFormat = LAYER_COORD_FORMAT[layerName] ?: "{z}/{x}/{y}"

        for (tile in tilesToCheck) {
            val tileKey = if (coordFormat == "{z}/{y}/{x}") {
                "${tile.z}/${tile.y}/${tile.x}"
            } else {
                "${tile.z}/${tile.x}/${tile.y}"
            }
            val tileFile = File(tilesDir, "$tileKey.png")
            val tileMeta = metadata[tileKey]

            // Count tile if it exists and is associated with this region
            if (tileFile.exists() && tileMeta?.regions?.contains(region.name) == true) {
                existingTiles++
                existingSize += tileFile.length()
            }
        }

        return RegionTileInfo(
            totalTiles = tilesToCheck.size,
            downloadedTiles = existingTiles,
            downloadedSize = existingSize,
            isFullyDownloaded = existingTiles == tilesToCheck.size
        )
    }

    /**
     * Delete all tiles for a specific region
     */
    fun deleteRegionTiles(region: Region, layerName: String = "kartverket") {
        val metadata = loadMetadata(layerName)
        val tilesDir = getTilesDir(layerName)
        var deletedCount = 0
        var freedSize = 0L

        // Remove region from metadata and delete orphaned tiles
        val keysToRemove = mutableListOf<String>()
        metadata.forEach { (tileKey, meta) ->
            if (meta.regions.contains(region.name)) {
                meta.regions.remove(region.name)

                // If no regions reference this tile anymore, delete it
                if (meta.regions.isEmpty()) {
                    val tileFile = File(tilesDir, "$tileKey.png")
                    if (tileFile.exists()) {
                        freedSize += tileFile.length()
                        tileFile.delete()
                        deletedCount++
                    }
                    keysToRemove.add(tileKey)
                }
            }
        }

        // Remove orphaned metadata entries
        keysToRemove.forEach { metadata.remove(it) }

        saveMetadata(layerName, metadata)
        Log.d("MapDownloadManager", "Deleted $deletedCount $layerName tiles for ${region.name}, freed ${freedSize / 1024}KB")
    }

    /**
     * Get total cache size and tile count
     */
    fun getTotalCacheInfo(): Pair<Long, Int> {
        var totalSize = 0L
        var tileCount = 0

        // Sum all layer directories
        val baseDir = File(context.getExternalFilesDir(null), "tiles")
        baseDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "png") {
                totalSize += file.length()
                tileCount++
            }
        }

        return Pair(totalSize, tileCount)
    }

    data class RegionTileInfo(
        val totalTiles: Int,
        val downloadedTiles: Int,
        val downloadedSize: Long,
        val isFullyDownloaded: Boolean
    )

    data class TileCoordinate(val z: Int, val x: Int, val y: Int)
}

