package no.synth.where.data.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import no.synth.where.data.TileUtils
import no.synth.where.data.geo.LatLng
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class RoutePoint(val latLng: LatLng, val elevation: Double)

data class RiverCrossing(val latLng: LatLng, val isRiver: Boolean)

data class TerrainRoute(
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val crossings: List<RiverCrossing>,
)

object TerrainRouter {
    /** How far (in cells) to look for a walkable cell when A or B sits on a blocked one. */
    private const val SNAP_RADIUS = 10
    private const val SMOOTH_RADIUS = 2

    // 16-neighbourhood: knight moves keep distances within ~3% of true, vs ~8% for 8 directions.
    private val DX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1, 1, 2, 2, 1, -1, -2, -2, -1)
    private val DY = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1, 2, 1, -1, -2, -2, -1, 1, 2)

    /** Fastest walking route from [start] to [end], or null when none exists. */
    suspend fun route(grid: TerrainGrid, start: LatLng, end: LatLng): TerrainRoute? = withContext(Dispatchers.Default) {
        val blocked = HikingCost.blockedCells(grid)
        val s = snap(grid, blocked, grid.latLngToIndex(start)) ?: return@withContext null
        val t = snap(grid, blocked, grid.latLngToIndex(end)) ?: return@withContext null
        val path = search(grid, blocked, s, t) ?: return@withContext null
        buildRoute(grid, path, blocked)
    }

    private suspend fun search(
        grid: TerrainGrid,
        blocked: BooleanArray,
        start: Int,
        target: Int,
    ): IntArray? {
        val w = grid.width
        val n = w * grid.height
        val c = grid.cellSizeMeters
        val g = FloatArray(n) { Float.POSITIVE_INFINITY }
        val cameFrom = IntArray(n) { -1 }
        val closed = BooleanArray(n)
        val heap = MinHeap()
        val tx = target % w
        val ty = target / w
        // Admissible: no step is faster than straight-line distance at Tobler's top speed.
        fun h(i: Int): Double {
            val dx = (i % w - tx).toDouble()
            val dy = (i / w - ty).toDouble()
            return sqrt(dx * dx + dy * dy) * c / HikingCost.maxSpeed
        }

        g[start] = 0f
        heap.push(start, h(start))
        var expanded = 0
        while (heap.isNotEmpty()) {
            val cur = heap.pop()
            if (closed[cur]) continue
            if (cur == target) return reconstruct(cameFrom, target)
            closed[cur] = true
            if (++expanded and 0xFFFF == 0) currentCoroutineContext().ensureActive()
            val cx = cur % w
            val cy = cur / w
            for (k in DX.indices) {
                val nx = cx + DX[k]
                val ny = cy + DY[k]
                if (nx !in 0 until w || ny !in 0 until grid.height) continue
                val next = grid.index(nx, ny)
                if (closed[next] || blocked[next]) continue
                if (k >= 8 && !knightPassable(grid, blocked, cx, cy, DX[k], DY[k])) continue
                if (k in 4..7 && blocked[grid.index(nx, cy)] && blocked[grid.index(cx, ny)]) continue
                val dist = c * sqrt((DX[k] * DX[k] + DY[k] * DY[k]).toDouble())
                val step = HikingCost.stepSeconds(grid, cur, next, dist) ?: continue
                val tentative = g[cur] + step.toFloat()
                if (tentative < g[next]) {
                    g[next] = tentative
                    cameFrom[next] = cur
                    heap.push(next, tentative + h(next))
                }
            }
        }
        return null
    }

    /**
     * A knight move passes through the two cells straddling it; both must be open and dry,
     * or the move would jump over a one-cell waterway without paying for the crossing.
     */
    private fun knightPassable(grid: TerrainGrid, blocked: BooleanArray, x: Int, y: Int, dx: Int, dy: Int): Boolean {
        val (a, b) = if (abs(dx) == 2) {
            grid.index(x + dx / 2, y) to grid.index(x + dx / 2, y + dy)
        } else {
            grid.index(x, y + dy / 2) to grid.index(x + dx, y + dy / 2)
        }
        val waterway = CellFlags.RIVER or CellFlags.STREAM
        return !blocked[a] && !blocked[b] && !grid.hasFlag(a, waterway) && !grid.hasFlag(b, waterway)
    }

    private fun reconstruct(cameFrom: IntArray, target: Int): IntArray {
        val out = ArrayList<Int>()
        var cur = target
        while (cur != -1) {
            out.add(cur)
            cur = cameFrom[cur]
        }
        out.reverse()
        return out.toIntArray()
    }

    private fun snap(grid: TerrainGrid, blocked: BooleanArray, index: Int): Int? {
        if (index < 0) return null
        if (!blocked[index]) return index
        val x0 = index % grid.width
        val y0 = index / grid.width
        for (r in 1..SNAP_RADIUS) {
            var best = -1
            var bestD = Int.MAX_VALUE
            for (y in max(0, y0 - r)..minOf(grid.height - 1, y0 + r)) {
                for (x in max(0, x0 - r)..minOf(grid.width - 1, x0 + r)) {
                    val i = grid.index(x, y)
                    val d = (x - x0) * (x - x0) + (y - y0) * (y - y0)
                    if (!blocked[i] && d < bestD) {
                        best = i
                        bestD = d
                    }
                }
            }
            if (best >= 0) return best
        }
        return null
    }

    private fun buildRoute(grid: TerrainGrid, path: IntArray, blocked: BooleanArray): TerrainRoute {
        val w = grid.width
        var distance = 0.0
        var seconds = 0.0
        val crossings = mutableListOf<RiverCrossing>()
        for (k in 1 until path.size) {
            val a = path[k - 1]
            val b = path[k]
            val dx = (b % w - a % w).toDouble()
            val dy = (b / w - a / w).toDouble()
            val d = sqrt(dx * dx + dy * dy) * grid.cellSizeMeters
            distance += d
            seconds += HikingCost.stepSeconds(grid, a, b, d) ?: 0.0
            if (grid.isWaterwayCrossing(b) && !grid.isWaterwayCrossing(a)) {
                crossings += RiverCrossing(grid.cellToLatLng(b % w, b / w), grid.hasFlag(b, CellFlags.RIVER))
            }
        }
        return TerrainRoute(trackPoints(grid, path, blocked), distance, seconds, crossings)
    }

    /** One point per path cell, smoothed so the 16-direction staircase doesn't show. */
    private fun trackPoints(grid: TerrainGrid, path: IntArray, blocked: BooleanArray): List<RoutePoint> {
        val w = grid.width
        val xs = DoubleArray(path.size) { path[it] % w + 0.5 }
        val ys = DoubleArray(path.size) { path[it] / w + 0.5 }
        val sx = smooth(xs)
        val sy = smooth(ys)
        return path.indices.map { i ->
            // Smoothing cuts corners; never let it pull a point into a lake or over a cliff edge.
            val cell = grid.index(sx[i].toInt().coerceIn(0, w - 1), sy[i].toInt().coerceIn(0, grid.height - 1))
            val (x, y) = if (blocked[cell]) xs[i] to ys[i] else sx[i] to sy[i]
            RoutePoint(TileUtils.worldPixelToLatLng(grid.originPx + x, grid.originPy + y, grid.zoom), grid.elevations[path[i]].toDouble())
        }
    }

    /** Moving average over [SMOOTH_RADIUS] cells each way; endpoints stay put. */
    private fun smooth(v: DoubleArray): DoubleArray {
        val n = v.size
        return DoubleArray(n) { i ->
            val r = minOf(SMOOTH_RADIUS, i, n - 1 - i)
            var sum = 0.0
            for (j in i - r..i + r) sum += v[j]
            sum / (2 * r + 1)
        }
    }
}

/** Binary min-heap of (cell, priority) with lazy deletion; duplicates are skipped via the closed set. */
private class MinHeap {
    private var items = IntArray(1024)
    private var prio = DoubleArray(1024)
    private var size = 0

    fun isNotEmpty() = size > 0

    fun push(item: Int, priority: Double) {
        if (size == items.size) {
            items = items.copyOf(size * 2)
            prio = prio.copyOf(size * 2)
        }
        var i = size++
        while (i > 0) {
            val parent = (i - 1) / 2
            if (prio[parent] <= priority) break
            items[i] = items[parent]
            prio[i] = prio[parent]
            i = parent
        }
        items[i] = item
        prio[i] = priority
    }

    fun pop(): Int {
        val top = items[0]
        size--
        if (size > 0) {
            val item = items[size]
            val p = prio[size]
            var i = 0
            while (true) {
                val l = 2 * i + 1
                if (l >= size) break
                val r = l + 1
                val child = if (r < size && prio[r] < prio[l]) r else l
                if (prio[child] >= p) break
                items[i] = items[child]
                prio[i] = prio[child]
                i = child
            }
            items[i] = item
            prio[i] = p
        }
        return top
    }
}
