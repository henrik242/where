package no.synth.where.data.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.util.mapConcurrently
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Small cliffs from Kartverket's 1 m terrain model, which the ~10 m elevation tiles smooth away.
 * Fetched at 2 m only around the route, since a whole routing area would be tens of megabytes.
 */
internal object CliffMap {
    private const val CHUNK_METERS = 500
    private const val SAMPLE_METERS = 2
    private const val SAMPLES = CHUNK_METERS / SAMPLE_METERS
    /** Squared gradient above which a sample is steep: 45 degrees, at least 4 m up over 4 m. */
    private const val STEEP_GRADIENT_SQ = 1.0
    /** Steep samples a cell needs to count as cliff; one alone is often noise. */
    private const val MIN_STEEP_SAMPLES = 2
    private const val CORRIDOR_METERS = 100.0
    /** The last pass only checks the route the one before it found. */
    private const val MAX_PASSES = 5
    /** A cell with fewer samples with data than this share is unknown terrain. */
    private const val MIN_VALID_SHARE = 0.5
    private const val PLANE_BYTES = (SAMPLES * SAMPLES + 7) / 8
    const val CACHE_SUBDIR = "cliff-masks"
    private const val CACHE_EXT = "bin"

    // Bilinear: the default nearest neighbour picks single 1 m samples, adding up to 2 m of noise.
    private const val WCS_URL = "https://wcs.geonorge.no/skwms1/wcs.hoyde-dtm-nhm-25833?service=WCS&version=1.0.0" +
        "&request=GetCoverage&coverage=nhm_dtm_topo_25833&format=GeoTIFF&crs=EPSG:25833&interpolation=bilinear"

    /** Mainland Norway, tighter than the download map's box: outside it Kartverket has no data. */
    private val COVERAGE = LatLngBounds(south = 57.8, west = 4.0, north = 71.4, east = 31.5)

    fun covers(p: LatLng) = p.latitude in COVERAGE.south..COVERAGE.north && p.longitude in COVERAGE.west..COVERAGE.east

    /**
     * [cliffs]: where [route] climbs a cliff, empty when it avoids them all.
     * [complete]: terrain data covered every cell of [route].
     */
    class Result(val route: TerrainRoute, val cliffs: List<LatLng>, val complete: Boolean)

    /**
     * Marks cliffs within [CORRIDOR_METERS] of [route] and reroutes around them, repeating while
     * the new route enters cells not checked yet. With no way around, settles for the route over
     * the fewest cliffs. Mapped paths are exempt when walked along.
     */
    suspend fun refine(grid: TerrainGrid, from: LatLng, to: LatLng, route: TerrainRoute): Result = withContext(Dispatchers.Default) {
        val checked = BooleanArray(grid.width * grid.height)
        val unknown = BooleanArray(grid.width * grid.height)
        val chunks = HashMap<Long, ByteArray?>()
        var current = route
        var cliffPenalty: Double? = null
        var settled = false
        for (pass in 0 until MAX_PASSES) {
            val cells = uncheckedCorridor(grid, current.cells, checked)
            if (cells.isNotEmpty()) markCliffs(grid, cells, chunks, unknown)
            val blocked = HikingCost.blockedCells(grid, cliffsPassable = cliffPenalty != null)
            // Avoiding cliffs: done once the route is clear. Climbing one: once no new cliffs turn up.
            settled = if (cliffPenalty == null) TerrainRouter.walkable(grid, current.cells, blocked) else cells.isEmpty()
            if (settled || pass == MAX_PASSES - 1) break
            current = TerrainRouter.route(grid, from, to, cliffPenalty, blocked)
                ?: if (cliffPenalty == null) {
                    cliffPenalty = HikingCost.CLIFF_PENALTY_SECONDS
                    TerrainRouter.route(grid, from, to, cliffPenalty) ?: break
                } else {
                    break
                }
        }
        // Smoothing ran before later passes found more cliffs: redo it against all of them.
        val final = TerrainRouter.rebuild(grid, current.cells, HikingCost.blockedCells(grid))
        Result(
            route = final,
            cliffs = TerrainRouter.cliffSpots(grid, final.cells),
            // Running out of passes leaves the last route unverified.
            complete = settled && final.cells.all { checked[it] && !unknown[it] },
        )
    }

    private suspend fun uncheckedCorridor(grid: TerrainGrid, route: IntArray, checked: BooleanArray): IntArray {
        val r = ceil(CORRIDOR_METERS / grid.cellSizeMeters).toInt()
        val out = ArrayList<Int>()
        for (cell in route) {
            currentCoroutineContext().ensureActive()
            val cx = cell % grid.width
            val cy = cell / grid.width
            for (y in maxOf(0, cy - r)..minOf(grid.height - 1, cy + r)) {
                for (x in maxOf(0, cx - r)..minOf(grid.width - 1, cx + r)) {
                    val i = grid.index(x, y)
                    if (!checked[i]) {
                        checked[i] = true
                        out += i
                    }
                }
            }
        }
        return out.toIntArray()
    }

    /** Global 2 m sample indices whose centres lie in a cell's footprint; y counts northwards. */
    private class Footprint(val x0: Int, val x1: Int, val y0: Int, val y1: Int)

    private fun footprint(e: Double, n: Double, half: Double): Footprint {
        // Sample i covers [2i, 2i + 2), centred on 2i + 1.
        fun first(v: Double) = ceil((v - half) / SAMPLE_METERS - 0.5).toInt()
        fun last(v: Double) = floor((v + half) / SAMPLE_METERS - 0.5).toInt()
        return Footprint(first(e), last(e), first(n), last(n))
    }

    private suspend fun markCliffs(grid: TerrainGrid, cells: IntArray, chunks: HashMap<Long, ByteArray?>, unknown: BooleanArray) {
        val half = grid.cellSizeMeters / 2
        val footprints = cells.map { cell ->
            val (e, n) = Utm33.project(grid.cellToLatLng(cell % grid.width, cell / grid.width))
            footprint(e, n, half)
        }
        val wanted = HashSet<Long>()
        for (f in footprints) {
            for (gx in intArrayOf(f.x0, f.x1)) for (gy in intArrayOf(f.y0, f.y1)) wanted += key(gx.floorDiv(SAMPLES), gy.floorDiv(SAMPLES))
        }
        val missing = wanted.filterNot { chunks.containsKey(it) }
        missing.zip(missing.mapConcurrently { loadChunk(keyX(it), keyY(it)) }).forEach { (k, mask) -> chunks[k] = mask }

        cells.forEachIndexed { k, cell ->
            if (k and 0xFFF == 0) currentCoroutineContext().ensureActive()
            val f = footprints[k]
            var steep = 0
            var valid = 0
            var total = 0
            for (gy in f.y0..f.y1) {
                for (gx in f.x0..f.x1) {
                    total++
                    val cx = gx.floorDiv(SAMPLES)
                    val cy = gy.floorDiv(SAMPLES)
                    val mask = chunks[key(cx, cy)] ?: continue
                    val bit = (SAMPLES - 1 - (gy - cy * SAMPLES)) * SAMPLES + (gx - cx * SAMPLES)
                    if (isSet(mask, PLANE_BYTES, bit)) continue
                    valid++
                    if (isSet(mask, 0, bit)) steep++
                }
            }
            if (steep >= MIN_STEEP_SAMPLES) grid.addFlag(cell, CellFlags.CLIFF)
            if (valid < total * MIN_VALID_SHARE) unknown[cell] = true
        }
    }

    private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
    private fun keyX(key: Long) = (key shr 32).toInt()
    private fun keyY(key: Long) = key.toInt()

    private fun isSet(mask: ByteArray, offset: Int, bit: Int) = mask[offset + (bit shr 3)].toInt() and (1 shl (bit and 7)) != 0

    private fun set(mask: ByteArray, offset: Int, bit: Int) {
        mask[offset + (bit shr 3)] = (mask[offset + (bit shr 3)].toInt() or (1 shl (bit and 7))).toByte()
    }

    /** WCS request for chunk ([cx], [cy]), with one sample of overlap so edge slopes have both neighbours. */
    internal fun chunkUrl(cx: Int, cy: Int): String {
        val e0 = cx * CHUNK_METERS - SAMPLE_METERS
        val n0 = cy * CHUNK_METERS - SAMPLE_METERS
        val e1 = e0 + CHUNK_METERS + 2 * SAMPLE_METERS
        val n1 = n0 + CHUNK_METERS + 2 * SAMPLE_METERS
        return "$WCS_URL&bbox=$e0,$n0,$e1,$n1&width=${SAMPLES + 2}&height=${SAMPLES + 2}"
    }

    /** A chunk's [steepMask]; null when unavailable or cached in another layout. */
    private suspend fun loadChunk(cx: Int, cy: Int): ByteArray? =
        OfflineTileReader.readCachedTile(chunkUrl(cx, cy), CHUNK_METERS, cx, cy, CACHE_SUBDIR, CACHE_EXT) { steepMask(FloatTiff.decode(it)) }
            ?.takeIf { it.size == 2 * PLANE_BYTES }

    /**
     * Two bit planes over the interior samples, rows north to south: central-difference gradient
     * above [STEEP_GRADIENT_SQ], then no data to tell.
     */
    internal fun steepMask(dem: FloatRaster): ByteArray {
        require(dem.width == SAMPLES + 2 && dem.height == SAMPLES + 2) { "unexpected size ${dem.width}x${dem.height}" }
        val mask = ByteArray(2 * PLANE_BYTES)
        val span = 2.0 * SAMPLE_METERS
        for (r in 0 until SAMPLES) {
            for (c in 0 until SAMPLES) {
                val bit = r * SAMPLES + c
                val dx = (z(dem, c + 2, r + 1) - z(dem, c, r + 1)) / span
                val dy = (z(dem, c + 1, r) - z(dem, c + 1, r + 2)) / span
                val g = dx * dx + dy * dy
                if (g.isNaN()) set(mask, PLANE_BYTES, bit) else if (g > STEEP_GRADIENT_SQ) set(mask, 0, bit)
            }
        }
        return mask
    }

    /**
     * Elevation, or NaN for no data. Beyond its coverage, e.g. inside Sweden, the service fills
     * with exactly 0; real sea level reads as small non-zero values.
     */
    private fun z(dem: FloatRaster, x: Int, y: Int): Double = dem[x, y].let { if (it == 0f) Double.NaN else it.toDouble() }
}
