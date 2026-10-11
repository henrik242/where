package no.synth.where.data.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.synth.where.data.DemTile
import no.synth.where.data.DemTileDecoder
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.TileUtils
import no.synth.where.data.TrackUtils
import no.synth.where.data.geo.LatLng
import no.synth.where.util.mapConcurrently
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

    /**
     * Builds an elevation grid covering [a] and [b] plus a margin scaled by [marginScale], or null
     * if tiles are missing or it would be too large.
     */
    suspend fun load(a: LatLng, b: LatLng, marginScale: Double = 1.0): TerrainGrid? = withContext(Dispatchers.Default) {
        var zoom = PREFERRED_ZOOM
        while (zoom > FALLBACK_ZOOM && cellCount(a, b, zoom, marginScale) > MAX_CELLS) zoom--
        if (cellCount(a, b, zoom, marginScale) > MAX_CELLS) return@withContext null
        loadAtZoom(a, b, zoom, marginScale) ?: if (zoom > FALLBACK_ZOOM) loadAtZoom(a, b, FALLBACK_ZOOM, marginScale) else null
    }

    private fun pixelBounds(a: LatLng, b: LatLng, zoom: Int, marginScale: Double): IntArray {
        val margin = max(MIN_MARGIN_METERS, a.distanceTo(b) * MARGIN_FRACTION) * marginScale
        val midLat = (a.latitude + b.latitude) / 2
        val marginPx = margin / TrackUtils.metersPerPixel(midLat, zoom.toDouble())
        val (ax, ay) = TileUtils.latLngToWorldPixel(a, zoom)
        val (bx, by) = TileUtils.latLngToWorldPixel(b, zoom)
        val worldMax = (TileUtils.TILE_SIZE shl zoom) - 1
        return intArrayOf(
            floor(min(ax, bx) - marginPx).toInt().coerceIn(0, worldMax),
            floor(min(ay, by) - marginPx).toInt().coerceIn(0, worldMax),
            floor(max(ax, bx) + marginPx).toInt().coerceIn(0, worldMax),
            floor(max(ay, by) + marginPx).toInt().coerceIn(0, worldMax),
        )
    }

    private fun cellCount(a: LatLng, b: LatLng, zoom: Int, marginScale: Double): Long {
        val (x0, y0, x1, y1) = pixelBounds(a, b, zoom, marginScale)
        return (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
    }

    private suspend fun loadAtZoom(a: LatLng, b: LatLng, zoom: Int, marginScale: Double): TerrainGrid? {
        val (x0, y0, x1, y1) = pixelBounds(a, b, zoom, marginScale)
        val size = TileUtils.TILE_SIZE
        val tiles = (y0 / size..y1 / size).flatMap { ty -> (x0 / size..x1 / size).map { tx -> tx to ty } }
        val decoded = tiles.mapConcurrently { (tx, ty) ->
            OfflineTileReader.readCachedTile(OfflineTileReader.DEM_TILE_URL, zoom, tx, ty)?.let { DemTileDecoder.decodeTile(it) }
        }
        val width = x1 - x0 + 1
        val height = y1 - y0 + 1
        val elevations = FloatArray(width * height)
        tiles.forEachIndexed { i, (tx, ty) ->
            val tile: DemTile = decoded[i] ?: return null
            if (tile.width != size || tile.height != size) return null
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
        return TerrainGrid(width, height, zoom, x0, y0, TrackUtils.metersPerPixel(midLat, zoom.toDouble()), elevations)
    }
}
