package no.synth.where.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.routing.VectorFeatureLoader
import no.synth.where.util.Logger
import kotlin.concurrent.Volatile

object OfflineTileReader {
    @Volatile
    private var cacheDir: PlatformFile? = null

    const val DEM_TILE_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
    private const val DEM_DOWNLOAD_ZOOM = 12
    private const val DEM_SUBDIR = "dem-tiles"
    private val TERRAIN_SUBDIRS = listOf(DEM_SUBDIR, VectorFeatureLoader.CACHE_SUBDIR)

    @Volatile
    var offlineOnly: Boolean = false

    fun init(cacheDir: PlatformFile) {
        this.cacheDir = cacheDir
    }

    suspend fun readCachedTile(tileUrl: String, z: Int, x: Int, y: Int): ByteArray? =
        readCachedTile(tileUrl, z, x, y, DEM_SUBDIR, "png")

    /**
     * Disk-cached tile fetch; [subdir] and [ext] keep each tile type in its own cache folder.
     * [transform] runs on freshly fetched bytes before they are cached, e.g. to strip unused data.
     */
    suspend fun readCachedTile(
        tileUrl: String, z: Int, x: Int, y: Int, subdir: String, ext: String,
        transform: (ByteArray) -> ByteArray = { it },
    ): ByteArray? =
        withContext(Dispatchers.Default) {
            val cached = readFromDiskCache(z, x, y, subdir, ext)
            if (cached != null) return@withContext cached
            if (offlineOnly) return@withContext null

            val fetched = fetchFromNetwork(tileUrl, z, x, y)?.let(transform) ?: return@withContext null
            writeToDiskCache(z, x, y, fetched, subdir, ext)
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
        return dir.resolve(DEM_SUBDIR).resolve("$z").resolve("$x").resolve("$y.png").exists()
    }

    /**
     * Offline terrain data for [bounds]: DEM tiles, plus the lake/river/path tiles the hiking route
     * planner needs. Progress covers both.
     */
    suspend fun downloadDemTilesForBounds(
        bounds: LatLngBounds,
        onProgress: (Int) -> Unit = {}
    ): Int = withContext(Dispatchers.Default) {
        val featureTemplate = VectorFeatureLoader.tileTemplate()
        val jobs = tilesInBounds(bounds, DEM_DOWNLOAD_ZOOM).map { (x, y) ->
            suspend { readCachedTile(DEM_TILE_URL, DEM_DOWNLOAD_ZOOM, x, y) }
        } + if (featureTemplate == null) emptyList() else tilesInBounds(bounds, VectorFeatureLoader.TILE_ZOOM).map { (x, y) ->
            suspend { VectorFeatureLoader.readTile(featureTemplate, x, y) }
        }
        if (jobs.isEmpty()) return@withContext 0
        val semaphore = Semaphore(6)
        val mutex = Mutex()
        var completed = 0
        jobs.map { job ->
            async<Unit> {
                semaphore.withPermit {
                    job()
                    mutex.withLock {
                        completed++
                        onProgress((completed * 100 / jobs.size).coerceIn(0, 100))
                    }
                }
            }
        }.awaitAll()
        Logger.d("Processed %d terrain tiles for bounds", jobs.size)
        completed
    }

    fun getDemCacheSize(): Long {
        val dir = cacheDir ?: return 0L
        return TERRAIN_SUBDIRS.sumOf { dir.resolve(it).totalSize() }
    }

    fun clearAllDemTiles() {
        val dir = cacheDir ?: return
        for (subdir in TERRAIN_SUBDIRS) dir.resolve(subdir).takeIf { it.exists() }?.deleteRecursively()
        Logger.d("Cleared all terrain tiles")
    }

    fun deleteDemTilesForBounds(bounds: LatLngBounds) {
        val deleted = deleteTiles(bounds, DEM_DOWNLOAD_ZOOM, DEM_SUBDIR, "png") +
            deleteTiles(bounds, VectorFeatureLoader.TILE_ZOOM, VectorFeatureLoader.CACHE_SUBDIR, VectorFeatureLoader.CACHE_EXT)
        Logger.d("Deleted %d terrain tiles for bounds", deleted)
    }

    private fun deleteTiles(bounds: LatLngBounds, zoom: Int, subdir: String, ext: String): Int {
        val dir = cacheDir ?: return 0
        var deleted = 0
        for ((x, y) in tilesInBounds(bounds, zoom)) {
            val file = dir.resolve(subdir).resolve("$zoom").resolve("$x").resolve("$y.$ext")
            if (file.exists()) {
                file.delete()
                deleted++
            }
        }
        return deleted
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

    private fun readFromDiskCache(z: Int, x: Int, y: Int, subdir: String = DEM_SUBDIR, ext: String = "png"): ByteArray? {
        val dir = cacheDir ?: return null
        val file = dir.resolve(subdir).resolve("$z").resolve("$x").resolve("$y.$ext")
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            if (bytes.isNotEmpty()) bytes else null
        } catch (e: Exception) {
            Logger.e(e, "Failed to read cached tile %s/%d/%d/%d", subdir, z, x, y)
            null
        }
    }

    private fun writeToDiskCache(z: Int, x: Int, y: Int, data: ByteArray, subdir: String = DEM_SUBDIR, ext: String = "png") {
        val dir = cacheDir ?: return
        try {
            val tileDir = dir.resolve(subdir).resolve("$z").resolve("$x")
            tileDir.mkdirs()
            tileDir.resolve("$y.$ext").writeBytes(data)
        } catch (e: Exception) {
            Logger.e(e, "Failed to cache tile %s/%d/%d/%d", subdir, z, x, y)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, "Failed to fetch tile %d/%d/%d", z, x, y)
            null
        }
    }
}
