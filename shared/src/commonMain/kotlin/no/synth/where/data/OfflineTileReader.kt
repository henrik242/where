package no.synth.where.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.util.Logger
import kotlin.concurrent.Volatile

object OfflineTileReader {
    @Volatile
    private var cacheDir: PlatformFile? = null

    const val DEM_TILE_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
    const val DEM_SUBDIR = "dem-tiles"

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
            readFromDiskCache(z, x, y, subdir, ext)?.let { return@withContext it }
            if (offlineOnly) return@withContext null
            fetchAndCache(tileUrl, z, x, y, subdir, ext, transform)
        }

    /** Fetches and caches the tile unless it's already on disk. Doesn't read cached tiles back. */
    suspend fun ensureCached(
        tileUrl: String, z: Int, x: Int, y: Int, subdir: String, ext: String,
        transform: (ByteArray) -> ByteArray = { it },
    ) {
        if (tileFile(z, x, y, subdir, ext)?.exists() != false) return
        fetchAndCache(tileUrl, z, x, y, subdir, ext, transform)
    }

    fun bestCachedZoom(lat: Double, lng: Double, maxZoom: Int): Int? {
        for (z in maxZoom downTo 0) {
            val coord = TileUtils.latLngToTileCoord(lat, lng, z)
            if (tileFile(coord.z, coord.x, coord.y, DEM_SUBDIR, "png")?.exists() == true) return z
        }
        return null
    }

    fun cacheSize(subdir: String): Long = cacheDir?.resolve(subdir)?.totalSize() ?: 0L

    fun clearCache(subdir: String) {
        cacheDir?.resolve(subdir)?.takeIf { it.exists() }?.deleteRecursively()
    }

    fun deleteTiles(bounds: LatLngBounds, zoom: Int, subdir: String, ext: String) {
        for ((x, y) in tilesInBounds(bounds, zoom)) {
            tileFile(zoom, x, y, subdir, ext)?.takeIf { it.exists() }?.delete()
        }
    }

    fun tilesInBounds(bounds: LatLngBounds, zoom: Int): List<Pair<Int, Int>> {
        val topLeft = TileUtils.latLngToTileCoord(bounds.north, bounds.west, zoom)
        val bottomRight = TileUtils.latLngToTileCoord(bounds.south, bounds.east, zoom)
        return (topLeft.x..bottomRight.x).flatMap { x -> (topLeft.y..bottomRight.y).map { y -> x to y } }
    }

    private fun tileFile(z: Int, x: Int, y: Int, subdir: String, ext: String): PlatformFile? =
        cacheDir?.resolve(subdir)?.resolve("$z")?.resolve("$x")?.resolve("$y.$ext")

    // An empty file is a valid tile: a vector tile with none of the wanted layers strips to nothing.
    private fun readFromDiskCache(z: Int, x: Int, y: Int, subdir: String, ext: String): ByteArray? {
        val file = tileFile(z, x, y, subdir, ext)?.takeIf { it.exists() } ?: return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            Logger.e(e, "Failed to read cached tile %s/%d/%d/%d", subdir, z, x, y)
            null
        }
    }

    private suspend fun fetchAndCache(
        tileUrl: String, z: Int, x: Int, y: Int, subdir: String, ext: String,
        transform: (ByteArray) -> ByteArray,
    ): ByteArray? {
        val raw = fetchFromNetwork(tileUrl, z, x, y) ?: return null
        // A truncated body or an HTML error page must not take down a whole region download.
        val data = try {
            transform(raw)
        } catch (e: Exception) {
            Logger.e(e, "Failed to process tile %s/%d/%d/%d", subdir, z, x, y)
            return null
        }
        try {
            val tileDir = cacheDir?.resolve(subdir)?.resolve("$z")?.resolve("$x") ?: return data
            tileDir.mkdirs()
            tileDir.resolve("$y.$ext").writeBytes(data)
        } catch (e: Exception) {
            Logger.e(e, "Failed to cache tile %s/%d/%d/%d", subdir, z, x, y)
        }
        return data
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
