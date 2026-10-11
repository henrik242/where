package no.synth.where.data.routing

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import no.synth.where.data.geo.LatLng
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class RoutePoint(val latLng: LatLng, val elevation: Double)

data class RiverCrossing(val latLng: LatLng, val isRiver: Boolean)

data class TerrainRoute(
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val ascentMeters: Double,
    val descentMeters: Double,
    val durationSeconds: Double,
    val crossings: List<RiverCrossing>,
)

sealed interface RouteResult {
    data class Found(val route: TerrainRoute) : RouteResult
    data object OutsideGrid : RouteResult
    data object NoRoute : RouteResult
}

object TerrainRouter {
    /** How far (in cells) to look for a walkable cell when A or B sits on a blocked one. */
    private const val SNAP_RADIUS = 10
    private const val SMOOTH_RADIUS = 2
    private const val SIMPLIFY_TOLERANCE_CELLS = 0.25
    private const val MAX_POINT_SPACING_METERS = 20.0

    // 16-neighbourhood: knight moves give smoother lines than 8 directions.
    private val DX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1, 1, 2, 2, 1, -1, -2, -2, -1)
    private val DY = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1, 2, 1, -1, -2, -2, -1, 1, 2)

    suspend fun route(
        grid: TerrainGrid,
        start: LatLng,
        end: LatLng,
        cost: HikingCost = HikingCost(),
        onProgress: (Float) -> Unit = {},
    ): RouteResult {
        val blocked = cost.blockedCells(grid)
        val rawStart = grid.latLngToIndex(start)
        val rawEnd = grid.latLngToIndex(end)
        if (rawStart < 0 || rawEnd < 0) return RouteResult.OutsideGrid
        val s = snap(grid, blocked, rawStart) ?: return RouteResult.NoRoute
        val t = snap(grid, blocked, rawEnd) ?: return RouteResult.NoRoute

        val path = search(grid, blocked, cost, s, t, onProgress) ?: return RouteResult.NoRoute
        return RouteResult.Found(buildRoute(grid, cost, path, blocked))
    }

    private suspend fun search(
        grid: TerrainGrid,
        blocked: BooleanArray,
        cost: HikingCost,
        start: Int,
        target: Int,
        onProgress: (Float) -> Unit,
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
        val totalH = heuristic(start % w, start / w, tx, ty, c, cost)

        fun h(i: Int) = heuristic(i % w, i / w, tx, ty, c, cost)

        g[start] = 0f
        heap.push(start, h(start))
        var expanded = 0
        while (heap.isNotEmpty()) {
            val cur = heap.pop()
            if (closed[cur]) continue
            if (cur == target) return reconstruct(cameFrom, target)
            closed[cur] = true
            if (++expanded and 0xFFFF == 0) {
                currentCoroutineContext().ensureActive()
                if (totalH > 0) onProgress((1.0 - h(cur) / totalH).toFloat().coerceIn(0f, 1f))
            }
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
                val step = cost.stepSeconds(grid, cur, next, dist) ?: continue
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
        val a: Int
        val b: Int
        if (abs(dx) == 2) {
            a = grid.index(x + dx / 2, y)
            b = grid.index(x + dx / 2, y + dy)
        } else {
            a = grid.index(x, y + dy / 2)
            b = grid.index(x + dx, y + dy / 2)
        }
        val waterway = CellFlags.RIVER or CellFlags.STREAM
        return !blocked[a] && !blocked[b] && !grid.hasFlag(a, waterway) && !grid.hasFlag(b, waterway)
    }

    private fun heuristic(x: Int, y: Int, tx: Int, ty: Int, cellSize: Double, cost: HikingCost): Double {
        val dx = (x - tx).toDouble()
        val dy = (y - ty).toDouble()
        return sqrt(dx * dx + dy * dy) * cellSize / cost.maxSpeed
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

    private fun buildRoute(grid: TerrainGrid, cost: HikingCost, path: IntArray, blocked: BooleanArray): TerrainRoute {
        val w = grid.width
        var distance = 0.0
        var ascent = 0.0
        var descent = 0.0
        var seconds = 0.0
        val crossings = mutableListOf<RiverCrossing>()
        for (k in 1 until path.size) {
            val a = path[k - 1]
            val b = path[k]
            val dx = (b % w - a % w).toDouble()
            val dy = (b / w - a / w).toDouble()
            val d = sqrt(dx * dx + dy * dy) * grid.cellSizeMeters
            distance += d
            seconds += cost.stepSeconds(grid, a, b, d) ?: 0.0
            val dz = grid.elevations[b] - grid.elevations[a]
            if (dz > 0) ascent += dz else descent -= dz
            if (grid.isWaterwayCrossing(b) && !grid.isWaterwayCrossing(a)) {
                crossings += RiverCrossing(grid.cellToLatLng(b % w, b / w), grid.hasFlag(b, CellFlags.RIVER))
            }
        }
        return TerrainRoute(trackPoints(grid, path, blocked), distance, ascent, descent, seconds, crossings)
    }

    /**
     * Turns the cell path into track points: smooths the 16-direction staircase, drops points
     * that add little (Douglas-Peucker), then fills gaps so no two points are more than
     * [MAX_POINT_SPACING_METERS] apart, keeping the line and elevation profile detailed.
     */
    internal fun trackPoints(grid: TerrainGrid, path: IntArray, blocked: BooleanArray): List<RoutePoint> {
        val w = grid.width
        val n = path.size
        val xs = DoubleArray(n) { path[it] % w + 0.5 }
        val ys = DoubleArray(n) { path[it] / w + 0.5 }
        val sx = smooth(xs)
        val sy = smooth(ys)
        // Smoothing cuts corners; never let it pull a point into a lake or over a cliff edge.
        for (i in 0 until n) {
            val cx = sx[i].toInt().coerceIn(0, w - 1)
            val cy = sy[i].toInt().coerceIn(0, grid.height - 1)
            if (blocked[grid.index(cx, cy)]) {
                sx[i] = xs[i]
                sy[i] = ys[i]
            }
        }
        val keep = simplify(sx, sy, SIMPLIFY_TOLERANCE_CELLS)
        val maxGapCells = MAX_POINT_SPACING_METERS / grid.cellSizeMeters
        val out = ArrayList<RoutePoint>()
        fun add(i: Int) {
            val latLng = Mercator.pixelToLatLng(grid.originPx + sx[i], grid.originPy + sy[i], grid.zoom)
            out += RoutePoint(latLng, grid.elevations[path[i]].toDouble())
        }
        var prev = 0
        add(0)
        for (k in 1 until n) {
            if (!keep[k]) continue
            // Walk the dropped stretch, re-adding a point whenever the gap would get too long.
            var last = prev
            for (i in prev + 1 until k) {
                val dx = sx[i + 1] - sx[last]
                val dy = sy[i + 1] - sy[last]
                if (sqrt(dx * dx + dy * dy) > maxGapCells) {
                    add(i)
                    last = i
                }
            }
            add(k)
            prev = k
        }
        return out
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

    /** Douglas-Peucker; returns which points to keep. */
    private fun simplify(xs: DoubleArray, ys: DoubleArray, toleranceCells: Double): BooleanArray {
        val n = xs.size
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (lo, hi) = stack.removeLast()
            if (hi - lo < 2) continue
            val ax = xs[lo]
            val ay = ys[lo]
            val bx = xs[hi]
            val by = ys[hi]
            val len = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
            var maxD = -1.0
            var maxI = -1
            for (i in lo + 1 until hi) {
                val px = xs[i]
                val py = ys[i]
                val d = if (len == 0.0) {
                    sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
                } else {
                    abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / len
                }
                if (d > maxD) {
                    maxD = d
                    maxI = i
                }
            }
            if (maxD > toleranceCells) {
                keep[maxI] = true
                stack.addLast(lo to maxI)
                stack.addLast(maxI to hi)
            }
        }
        return keep
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
