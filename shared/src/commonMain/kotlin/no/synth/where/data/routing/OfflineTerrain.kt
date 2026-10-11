package no.synth.where.data.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.util.mapConcurrently

/** Offline terrain data: elevation tiles, plus the lake/river/path tiles the route planner needs. */
object OfflineTerrain {
    private const val DEM_ZOOM = 12

    suspend fun download(bounds: LatLngBounds, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.Default) {
        val template = VectorFeatureLoader.tileTemplate()
        val jobs = OfflineTileReader.tilesInBounds(bounds, DEM_ZOOM).map { (x, y) ->
            suspend {
                OfflineTileReader.ensureCached(OfflineTileReader.DEM_TILE_URL, DEM_ZOOM, x, y, OfflineTileReader.DEM_SUBDIR, "png")
            }
        } + if (template == null) emptyList() else OfflineTileReader.tilesInBounds(bounds, VectorFeatureLoader.TILE_ZOOM).map { (x, y) ->
            suspend { VectorFeatureLoader.ensureCached(template, x, y) }
        }
        val mutex = Mutex()
        var done = 0
        jobs.mapConcurrently { job ->
            job()
            mutex.withLock { onProgress(++done * 100 / jobs.size) }
        }
    }

    fun delete(bounds: LatLngBounds) {
        OfflineTileReader.deleteTiles(bounds, DEM_ZOOM, OfflineTileReader.DEM_SUBDIR, "png")
        OfflineTileReader.deleteTiles(bounds, VectorFeatureLoader.TILE_ZOOM, VectorFeatureLoader.CACHE_SUBDIR, VectorFeatureLoader.CACHE_EXT)
    }

    fun cacheSize(): Long =
        OfflineTileReader.cacheSize(OfflineTileReader.DEM_SUBDIR) + OfflineTileReader.cacheSize(VectorFeatureLoader.CACHE_SUBDIR)

    fun clear() {
        OfflineTileReader.clearCache(OfflineTileReader.DEM_SUBDIR)
        OfflineTileReader.clearCache(VectorFeatureLoader.CACHE_SUBDIR)
    }
}
