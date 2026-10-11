package no.synth.where.data.routing

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerrainRouterTest {
    private val zoom = 13
    private val originPx = 1_100_000
    private val originPy = 600_000

    private fun grid(w: Int, h: Int, elevation: (Int, Int) -> Float = { _, _ -> 100f }) =
        TerrainGrid(w, h, zoom, originPx, originPy, 10.0, FloatArray(w * h) { elevation(it % w, it / w) })

    private suspend fun routeCells(g: TerrainGrid, from: Pair<Int, Int>, to: Pair<Int, Int>): TerrainRoute {
        return assertNotNull(TerrainRouter.route(g, g.cellToLatLng(from.first, from.second), g.cellToLatLng(to.first, to.second)))
    }

    private fun TerrainGrid.cellsOf(route: TerrainRoute) = route.points.map { latLngToIndex(it.latLng) }

    @Test
    fun flatGroundIsStraight() = runTest {
        val g = grid(60, 30)
        val route = routeCells(g, 5 to 15, 55 to 15)
        assertEquals(500.0, route.distanceMeters, 1.0)
        assertTrue(route.crossings.isEmpty())
        // One point per cell, all on the straight line.
        val gaps = route.points.zipWithNext { p, q -> p.latLng.distanceTo(q.latLng) }
        assertTrue(gaps.all { it <= 20.5 }, "gaps=$gaps")
        assertTrue(route.points.size >= 25)
        val lat = route.points.first().latLng.latitude
        assertTrue(route.points.all { abs(it.latLng.latitude - lat) < 1e-9 })
    }

    @Test
    fun goesAroundLake() = runTest {
        val g = grid(60, 40)
        for (y in 5 until 35) for (x in 25 until 35) g.addFlag(g.index(x, y), CellFlags.WATER)
        val route = routeCells(g, 5 to 20, 55 to 20)
        val cells = g.cellsOf(route)
        assertTrue(cells.none { g.hasFlag(it, CellFlags.WATER) })
        assertTrue(route.distanceMeters > 500.0)
    }

    @Test
    fun avoidsCliffBandThroughGap() = runTest {
        // A 50 m step between rows 19 and 20 everywhere except a gentle ramp near x = 50.
        val g = grid(60, 40) { x, y ->
            val step = if (y >= 20) 50f else 0f
            if (x in 46..54) (y.coerceIn(10, 30) - 10) * 2.5f else step
        }
        val route = routeCells(g, 10 to 5, 10 to 35)
        val xs = g.cellsOf(route).map { it % g.width }
        assertTrue(xs.any { it in 46..54 }, "route should use the ramp, went through x=$xs")
    }

    @Test
    fun reportsSingleStreamCrossingAndPrefersBridge() = runTest {
        val g = grid(60, 40)
        for (x in 0 until 60) g.addFlag(g.index(x, 20), CellFlags.STREAM)
        val noBridge = routeCells(g, 30 to 5, 30 to 35)
        assertEquals(1, noBridge.crossings.size)

        g.addFlag(g.index(33, 20), CellFlags.BRIDGE)
        val withBridge = routeCells(g, 30 to 5, 30 to 35)
        assertTrue(withBridge.crossings.isEmpty())
    }

    @Test
    fun riverCrossingIsFlaggedAsRiver() = runTest {
        val g = grid(40, 40)
        for (x in 0 until 40) g.addFlag(g.index(x, 20), CellFlags.RIVER)
        val route = routeCells(g, 20 to 5, 20 to 35)
        assertEquals(1, route.crossings.size)
        assertTrue(route.crossings.single().isRiver)
    }

    @Test
    fun cannotDropOntoPathFromCliffAbove() {
        // A path along the foot of a 30 m wall: stepping down onto it is still a cliff.
        val g = grid(40, 40) { _, y -> if (y < 20) 30f else 0f }
        for (x in 0 until 40) g.addFlag(g.index(x, 20), CellFlags.PATH)
        val down = g.index(10, 20)
        assertNull(HikingCost.stepSeconds(g, g.index(10, 19), down, 10.0))
        assertNotNull(HikingCost.stepSeconds(g, g.index(11, 20), down, 10.0))
    }

    @Test
    fun walledInTargetHasNoRoute() = runTest {
        val g = grid(40, 40)
        for (y in 0 until 40) for (x in 0 until 40) {
            if (abs(x - 30) > 3 || abs(y - 30) > 3) continue
            if (abs(x - 30) == 3 || abs(y - 30) == 3) g.addFlag(g.index(x, y), CellFlags.WATER)
        }
        assertNull(TerrainRouter.route(g, g.cellToLatLng(5, 5), g.cellToLatLng(30, 30)))
    }

    @Test
    fun matchesDijkstraOnHillyTerrain() = runTest {
        val g = grid(40, 40) { x, y -> (kotlin.math.sin(x / 4.0) * 15 + kotlin.math.cos(y / 5.0) * 15).toFloat() }
        val route = routeCells(g, 2 to 3, 37 to 36)
        val best = dijkstra(g, g.index(2, 3), g.index(37, 36))
        assertEquals(best, route.durationSeconds, best * 1e-4)
    }

    /** Reference: plain Dijkstra over the same 16-neighbourhood, no heuristic. */
    private fun dijkstra(g: TerrainGrid, start: Int, target: Int): Double {
        val dx = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1, 1, 2, 2, 1, -1, -2, -2, -1)
        val dy = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1, 2, 1, -1, -2, -2, -1, 1, 2)
        val dist = DoubleArray(g.width * g.height) { Double.POSITIVE_INFINITY }
        val done = BooleanArray(dist.size)
        dist[start] = 0.0
        while (true) {
            var cur = -1
            for (i in dist.indices) if (!done[i] && (cur < 0 || dist[i] < dist[cur])) cur = i
            if (cur < 0 || dist[cur].isInfinite()) return Double.POSITIVE_INFINITY
            if (cur == target) return dist[cur]
            done[cur] = true
            for (k in dx.indices) {
                val nx = cur % g.width + dx[k]
                val ny = cur / g.width + dy[k]
                if (nx !in 0 until g.width || ny !in 0 until g.height) continue
                val next = g.index(nx, ny)
                val d = 10.0 * kotlin.math.sqrt((dx[k] * dx[k] + dy[k] * dy[k]).toDouble())
                val step = HikingCost.stepSeconds(g, cur, next, d) ?: continue
                if (dist[cur] + step < dist[next]) dist[next] = dist[cur] + step
            }
        }
    }
}
