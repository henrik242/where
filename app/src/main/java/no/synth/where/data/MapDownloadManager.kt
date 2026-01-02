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
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * Manual tile downloader for Kartverket maps.
 *
 * MapLibre's built-in offline manager has issues with custom tile sources,
 * so we download tiles manually and store them in a directory structure
 * that MapLibre can use offline.
 */
class MapDownloadManager(private val context: Context) {

    private val tileBaseUrl = "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator"
    private val tilesDir = File(context.getExternalFilesDir(null), "tiles/kartverket")

    /**
     * Download map tiles for a region
     */
    suspend fun downloadRegion(
        region: Region,
        minZoom: Int = 5,
        maxZoom: Int = 15,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("MapDownloadManager", "Starting manual tile download for: ${region.name}")
                Log.d("MapDownloadManager", "Zoom levels: $minZoom - $maxZoom")

                // Create tiles directory
                tilesDir.mkdirs()

                // Calculate tile ranges for each zoom level
                val tilesToDownload = mutableListOf<TileCoordinate>()
                for (zoom in minZoom..maxZoom) {
                    val tiles = getTilesForBounds(region.boundingBox, zoom)
                    tilesToDownload.addAll(tiles)
                }

                Log.d("MapDownloadManager", "Total tiles to download: ${tilesToDownload.size}")

                var downloaded = 0
                var failed = 0

                for ((index, tile) in tilesToDownload.withIndex()) {
                    val success = downloadTile(tile)
                    if (success) {
                        downloaded++
                    } else {
                        failed++
                    }

                    val progress = ((index + 1) * 100) / tilesToDownload.size
                    onProgress(progress)

                    if (index % 10 == 0) {
                        Log.d("MapDownloadManager", "Progress: $progress% ($downloaded downloaded, $failed failed)")
                    }
                }

                Log.d("MapDownloadManager", "Download complete: $downloaded succeeded, $failed failed")
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

    private suspend fun downloadTile(tile: TileCoordinate): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$tileBaseUrl/${tile.z}/${tile.x}/${tile.y}.png"
                val tileFile = File(tilesDir, "${tile.z}/${tile.x}/${tile.y}.png")

                // Skip if already downloaded
                if (tileFile.exists()) {
                    return@withContext true
                }

                // Create directory
                tileFile.parentFile?.mkdirs()

                // Download tile
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Hvor/1.0")

                if (connection.responseCode == 200) {
                    connection.inputStream.use { input ->
                        FileOutputStream(tileFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } else {
                    Log.w("MapDownloadManager", "Failed to download tile: ${connection.responseCode} for $url")
                    false
                }
            } catch (e: Exception) {
                Log.w("MapDownloadManager", "Error downloading tile ${tile.z}/${tile.x}/${tile.y}", e)
                false
            }
        }
    }

    data class TileCoordinate(val z: Int, val x: Int, val y: Int)
}

