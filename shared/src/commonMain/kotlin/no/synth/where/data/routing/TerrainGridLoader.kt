package no.synth.where.data.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import no.synth.where.data.DemTile
import no.synth.where.data.DemTileDecoder
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.geo.LatLng
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object TerrainGridLoader {
    /** Terrarium in Norway is ~10 m source data; z13 gives ~10 m cells at 60N. */
    private const val PREFERRED_ZOOM = 13
    /** Zoom of offline DEM downloads, used when the preferred zoom isn't available. */
    private const val FALLBACK_ZOOM = 12
    private const val MAX_CELLS = 4_000_000
    private const val MIN_MARGIN_METERS = 1000.0
    private const val MARGIN_FRACTION = 0.25

    /** Builds an elevation grid covering [a] and [b] with a margin, or null if tiles are missing. */
    suspend fun load(a: LatLng, b: LatLng): TerrainGrid? = withContext(Dispatchers.Default) {
        var zoom = PREFERRED_ZOOM
        while (zoom > FALLBACK_ZOOM && cellCount(a, b, zoom) > MAX_CELLS) zoom--
        if (cellCount(a, b, zoom) > MAX_CELLS) return@withContext null
        loadAtZoom(a, b, zoom) ?: if (zoom > FALLBACK_ZOOM) loadAtZoom(a, b, FALLBACK_ZOOM) else null
    }

    private fun pixelBounds(a: LatLng, b: LatLng, zoom: Int): IntArray {
        val margin = max(MIN_MARGIN_METERS, a.distanceTo(b) * MARGIN_FRACTION)
        val midLat = (a.latitude + b.latitude) / 2
        val marginPx = margin / Mercator.metersPerPixel(midLat, zoom)
        val (ax, ay) = Mercator.latLngToPixel(a, zoom)
        val (bx, by) = Mercator.latLngToPixel(b, zoom)
        val worldMax = Mercator.worldSize(zoom).toInt() - 1
        return intArrayOf(
            floor(min(ax, bx) - marginPx).toInt().coerceIn(0, worldMax),
            floor(min(ay, by) - marginPx).toInt().coerceIn(0, worldMax),
            floor(max(ax, bx) + marginPx).toInt().coerceIn(0, worldMax),
            floor(max(ay, by) + marginPx).toInt().coerceIn(0, worldMax),
        )
    }

    private fun cellCount(a: LatLng, b: LatLng, zoom: Int): Long {
        val (x0, y0, x1, y1) = pixelBounds(a, b, zoom)
        return (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
    }

    private suspend fun loadAtZoom(a: LatLng, b: LatLng, zoom: Int): TerrainGrid? = coroutineScope {
        val (x0, y0, x1, y1) = pixelBounds(a, b, zoom)
        val size = Mercator.TILE_SIZE
        val tiles = (y0 / size..y1 / size).flatMap { ty -> (x0 / size..x1 / size).map { tx -> tx to ty } }
        val semaphore = Semaphore(6)
        val decoded = tiles.map { (tx, ty) ->
            async {
                semaphore.withPermit {
                    OfflineTileReader.readCachedTile(OfflineTileReader.DEM_TILE_URL, zoom, tx, ty)
                        ?.let { DemTileDecoder.decodeTile(it) }
                }
            }
        }.awaitAll()
        val width = x1 - x0 + 1
        val height = y1 - y0 + 1
        val elevations = FloatArray(width * height)
        tiles.forEachIndexed { i, (tx, ty) ->
            val tile: DemTile = decoded[i] ?: return@coroutineScope null
            if (tile.width != size || tile.height != size) return@coroutineScope null
            // Copy the overlap of this tile with the grid, row by row.
            val fromX = max(x0, tx * size)
            val toX = min(x1, tx * size + size - 1)
            for (py in max(y0, ty * size)..min(y1, ty * size + size - 1)) {
                tile.elevations.copyInto(
                    elevations,
                    destinationOffset = (py - y0) * width + (fromX - x0),
                    startIndex = (py - ty * size) * size + (fromX - tx * size),
                    endIndex = (py - ty * size) * size + (toX - tx * size) + 1,
                )
            }
        }
        val midLat = (a.latitude + b.latitude) / 2
        TerrainGrid(width, height, zoom, x0, y0, Mercator.metersPerPixel(midLat, zoom), elevations)
    }
}
