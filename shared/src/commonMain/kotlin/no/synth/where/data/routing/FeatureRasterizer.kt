package no.synth.where.data.routing

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Burns OpenMapTiles features from one vector tile into [TerrainGrid.flags]. */
object FeatureRasterizer {
    val LAYERS = setOf("water", "waterway", "transportation", "landcover")

    // Not walkable, or not something a hiker should follow.
    private val NON_WALKABLE_TRANSPORT = setOf("motorway", "ferry", "rail", "transit", "raceway", "aerialway")

    // Standing water; river/stream areas stay crossable as rivers.
    private val RIVER_AREA_CLASSES = setOf("river")

    fun burnTile(grid: TerrainGrid, layers: List<MvtDecoder.Layer>, tileZ: Int, tileX: Int, tileY: Int) {
        for (layer in layers) {
            // Tile coordinates -> grid cell coordinates (fractional).
            val scale = Mercator.TILE_SIZE.toDouble() / layer.extent * pow2(grid.zoom - tileZ)
            val offX = tileX * Mercator.TILE_SIZE * pow2(grid.zoom - tileZ) - grid.originPx
            val offY = tileY * Mercator.TILE_SIZE * pow2(grid.zoom - tileZ) - grid.originPy
            fun toGrid(part: IntArray) = DoubleArray(part.size) { i ->
                part[i] * scale + if (i % 2 == 0) offX else offY
            }
            for (f in layer.features) {
                val flag = flagFor(layer.name, f) ?: continue
                val parts = f.parts.map(::toGrid)
                when (f.type) {
                    MvtDecoder.GeomType.POLYGON -> fillPolygon(grid, parts, flag)
                    MvtDecoder.GeomType.LINESTRING -> parts.forEach { drawLine(grid, it, flag) }
                    else -> Unit
                }
            }
        }
    }

    internal fun flagFor(layer: String, f: MvtDecoder.Feature): Int? {
        val cls = f.tags["class"] as? String
        val brunnel = f.tags["brunnel"] as? String
        return when (layer) {
            "water" -> when {
                brunnel == "tunnel" -> null
                cls in RIVER_AREA_CLASSES -> CellFlags.RIVER
                else -> CellFlags.WATER
            }
            "waterway" -> when {
                brunnel == "tunnel" -> null
                cls == "river" -> CellFlags.RIVER
                else -> CellFlags.STREAM
            }
            "transportation" -> when {
                cls == null || cls in NON_WALKABLE_TRANSPORT -> null
                brunnel == "bridge" -> CellFlags.BRIDGE or CellFlags.PATH
                brunnel == "tunnel" -> null
                else -> CellFlags.PATH
            }
            "landcover" -> when (cls) {
                "wetland" -> CellFlags.WETLAND
                "ice" -> CellFlags.GLACIER
                else -> null
            }
            else -> null
        }
    }

    /**
     * 4-connected line: when a step moves diagonally, the corner cell is marked too, so nothing
     * can cross the line diagonally without touching it.
     */
    internal fun drawLine(grid: TerrainGrid, pts: DoubleArray, flag: Int) {
        var i = 0
        while (i + 3 < pts.size) {
            var x = floor(pts[i]).toInt()
            var y = floor(pts[i + 1]).toInt()
            val x1 = floor(pts[i + 2]).toInt()
            val y1 = floor(pts[i + 3]).toInt()
            val dx = abs(x1 - x)
            val dy = -abs(y1 - y)
            val sx = if (x < x1) 1 else -1
            val sy = if (y < y1) 1 else -1
            var err = dx + dy
            mark(grid, x, y, flag)
            while (x != x1 || y != y1) {
                val e2 = 2 * err
                if (e2 >= dy && x != x1) {
                    err += dy
                    x += sx
                    mark(grid, x, y, flag)
                }
                if (e2 <= dx && y != y1) {
                    err += dx
                    y += sy
                    mark(grid, x, y, flag)
                }
            }
            i += 2
        }
    }

    /** Even-odd scanline fill over all rings, so holes (islands) stay open. */
    internal fun fillPolygon(grid: TerrainGrid, rings: List<DoubleArray>, flag: Int) {
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (r in rings) for (k in 1 until r.size step 2) {
            minY = min(minY, r[k])
            maxY = max(maxY, r[k])
        }
        if (minY > maxY) return
        val yStart = max(0, floor(minY).toInt())
        val yEnd = min(grid.height - 1, ceil(maxY).toInt())
        val xs = ArrayList<Double>()
        for (y in yStart..yEnd) {
            val sy = y + 0.5
            xs.clear()
            for (r in rings) {
                var k = 0
                while (k + 3 < r.size) {
                    val ax = r[k]
                    val ay = r[k + 1]
                    val bx = r[k + 2]
                    val by = r[k + 3]
                    if ((ay <= sy && by > sy) || (by <= sy && ay > sy)) {
                        xs += ax + (sy - ay) / (by - ay) * (bx - ax)
                    }
                    k += 2
                }
            }
            xs.sort()
            var j = 0
            while (j + 1 < xs.size) {
                val from = max(0, ceil(xs[j] - 0.5).toInt())
                val to = min(grid.width - 1, floor(xs[j + 1] - 0.5).toInt())
                for (x in from..to) grid.addFlag(grid.index(x, y), flag)
                j += 2
            }
        }
    }

    private fun mark(grid: TerrainGrid, x: Int, y: Int, flag: Int) {
        if (x in 0 until grid.width && y in 0 until grid.height) grid.addFlag(grid.index(x, y), flag)
    }

    private fun pow2(n: Int) = if (n >= 0) (1 shl n).toDouble() else 1.0 / (1 shl -n)
}
