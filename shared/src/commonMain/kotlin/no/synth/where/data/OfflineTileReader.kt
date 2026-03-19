package no.synth.where.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.util.Logger
import kotlin.concurrent.Volatile

object OfflineTileReader {
    @Volatile
    private var cacheDir: PlatformFile? = null

    const val DEM_TILE_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
    private const val DEM_DOWNLOAD_ZOOM = 12

    @Volatile
    var offlineOnly: Boolean = false

    fun init(cacheDir: PlatformFile) {
        this.cacheDir = cacheDir
    }

    suspend fun readCachedTile(tileUrl: String, z: Int, x: Int, y: Int): ByteArray? =
        withContext(Dispatchers.Default) {
            val cached = readFromDiskCache(z, x, y)
            if (cached != null) return@withContext cached
            if (offlineOnly) return@withContext null

            val fetched = fetchFromNetwork(tileUrl, z, x, y) ?: return@withContext null
            writeToDiskCache(z, x, y, fetched)
            fetched
        }

    fun bestCachedZoom(lat: Double, lng: Double, maxZoom: Int): Int? {
        for (z in maxZoom downTo 0) {
            val coord = TileUtils.latLngToTileCoord(lat, lng, z)
            if (hasCachedTile(coord.z, coord.x, coord.y)) return z
        }
        return null
    }

    fun hasCachedTile(z: Int, x: Int, y: Int): Boolean {
        val dir = cacheDir ?: return false
        return dir.resolve("dem-tiles").resolve("$z").resolve("$x").resolve("$y.png").exists()
    }

    suspend fun downloadDemTilesForBounds(
        bounds: LatLngBounds,
        onProgress: (Int) -> Unit = {}
    ): Int = withContext(Dispatchers.Default) {
        val zoom = DEM_DOWNLOAD_ZOOM
        val tiles = tilesInBounds(bounds, zoom)
        if (tiles.isEmpty()) return@withContext 0
        val semaphore = Semaphore(6)
        val mutex = Mutex()
        var completed = 0
        val total = tiles.size
        tiles.map { (x, y) ->
            async<Unit> {
                semaphore.withPermit {
                    if (!hasCachedTile(zoom, x, y)) {
                        val data = fetchFromNetwork(DEM_TILE_URL, zoom, x, y)
                        if (data != null) {
                            writeToDiskCache(zoom, x, y, data)
                        }
                    }
                    mutex.withLock {
                        completed++
                        onProgress((completed * 100 / total).coerceIn(0, 100))
                    }
                }
            }
        }.awaitAll()
        Logger.d("Processed %d DEM tiles at z%d for bounds", total, zoom)
        completed
    }

    fun getDemCacheSize(): Long {
        val dir = cacheDir?.resolve("dem-tiles") ?: return 0L
        return dir.totalSize()
    }

    fun clearAllDemTiles() {
        val dir = cacheDir?.resolve("dem-tiles") ?: return
        if (dir.exists()) dir.deleteRecursively()
        Logger.d("Cleared all DEM tiles")
    }

    fun deleteDemTilesForBounds(bounds: LatLngBounds) {
        val zoom = DEM_DOWNLOAD_ZOOM
        val tiles = tilesInBounds(bounds, zoom)
        var deleted = 0
        for ((x, y) in tiles) {
            val dir = cacheDir ?: return
            val file = dir.resolve("dem-tiles").resolve("$zoom").resolve("$x").resolve("$y.png")
            if (file.exists()) {
                file.delete()
                deleted++
            }
        }
        Logger.d("Deleted %d DEM tiles at z%d for bounds", deleted, zoom)
    }

    private fun tilesInBounds(bounds: LatLngBounds, zoom: Int): List<Pair<Int, Int>> {
        val topLeft = TileUtils.latLngToTileCoord(bounds.north, bounds.west, zoom)
        val bottomRight = TileUtils.latLngToTileCoord(bounds.south, bounds.east, zoom)
        val tiles = mutableListOf<Pair<Int, Int>>()
        for (x in topLeft.x..bottomRight.x) {
            for (y in topLeft.y..bottomRight.y) {
                tiles.add(Pair(x, y))
            }
        }
        return tiles
    }

    private fun readFromDiskCache(z: Int, x: Int, y: Int): ByteArray? {
        val dir = cacheDir ?: return null
        val file = dir.resolve("dem-tiles").resolve("$z").resolve("$x").resolve("$y.png")
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            if (bytes.isNotEmpty()) bytes else null
        } catch (e: Exception) {
            Logger.e(e, "Failed to read cached DEM tile %d/%d/%d", z, x, y)
            null
        }
    }

    private fun writeToDiskCache(z: Int, x: Int, y: Int, data: ByteArray) {
        val dir = cacheDir ?: return
        try {
            val tileDir = dir.resolve("dem-tiles").resolve("$z").resolve("$x")
            tileDir.mkdirs()
            tileDir.resolve("$y.png").writeBytes(data)
        } catch (e: Exception) {
            Logger.e(e, "Failed to cache DEM tile %d/%d/%d", z, x, y)
        }
    }

    private suspend fun fetchFromNetwork(tileUrl: String, z: Int, x: Int, y: Int): ByteArray? {
        return try {
            val url = tileUrl
                .replace("{z}", z.toString())
                .replace("{x}", x.toString())
                .replace("{y}", y.toString())
            val response = TerrainClient.client.get(url)
            if (response.status.value in 200..299) {
                response.bodyAsBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(e, "Failed to fetch DEM tile %d/%d/%d", z, x, y)
            null
        }
    }
}
