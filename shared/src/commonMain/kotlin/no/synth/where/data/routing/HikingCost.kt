package no.synth.where.data.routing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tan

/** Tunables for the walking-time cost. Times are in seconds. */
data class HikingCost(
    /** Cells steeper than this, or steps rising/falling faster, are impassable (cliff proxy). */
    val maxSlopeDegrees: Double = 40.0,
    /** Tobler's off-path factor: off-trail walking takes 1/0.6 of on-trail time. */
    val offTrailFactor: Double = 1.0 / 0.6,
    val wetlandFactor: Double = 1.5,
    val streamCrossingSeconds: Double = 60.0,
    val riverCrossingSeconds: Double = 900.0,
    /** Time multiplier for walking along inside a waterway cell. */
    val inWaterwayFactor: Double = 3.0,
) {
    val maxGradient = tan(maxSlopeDegrees * PI / 180.0)

    /** Fastest possible speed in m/s, for an admissible A* heuristic. */
    val maxSpeed = toblerSpeed(-0.05)

    /** Tobler's hiking function: walking speed in m/s for gradient dh/dx. */
    fun toblerSpeed(gradient: Double) = 6.0 * exp(-3.5 * abs(gradient + 0.05)) / 3.6

    /** Marks cells whose own slope (central differences) exceeds [maxSlopeDegrees], plus open water and glaciers. */
    fun blockedCells(grid: TerrainGrid): BooleanArray {
        val w = grid.width
        val h = grid.height
        val e = grid.elevations
        val c = grid.cellSizeMeters
        val blocked = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = grid.index(x, y)
                if (grid.hasFlag(i, CellFlags.WATER or CellFlags.GLACIER) && !grid.hasFlag(i, CellFlags.BRIDGE)) {
                    blocked[i] = true
                    continue
                }
                // Mapped paths and bridges stay walkable even where the DEM is noisy.
                if (grid.hasFlag(i, CellFlags.PATH) || grid.hasFlag(i, CellFlags.BRIDGE)) continue
                val x0 = if (x > 0) x - 1 else x
                val x1 = if (x < w - 1) x + 1 else x
                val y0 = if (y > 0) y - 1 else y
                val y1 = if (y < h - 1) y + 1 else y
                val dzdx = if (x1 > x0) (e[grid.index(x1, y)] - e[grid.index(x0, y)]) / ((x1 - x0) * c) else 0.0
                val dzdy = if (y1 > y0) (e[grid.index(x, y1)] - e[grid.index(x, y0)]) / ((y1 - y0) * c) else 0.0
                if (sqrt(dzdx * dzdx + dzdy * dzdy) > maxGradient) blocked[i] = true
            }
        }
        return blocked
    }

    /**
     * Seconds to walk from cell [from] to adjacent cell [to] over horizontal [distance] meters,
     * or null when the step is impassable.
     */
    fun stepSeconds(grid: TerrainGrid, from: Int, to: Int, distance: Double): Double? {
        val gradient = (grid.elevations[to] - grid.elevations[from]) / distance
        val onPath = grid.hasFlag(to, CellFlags.PATH) || grid.hasFlag(to, CellFlags.BRIDGE)
        if (!onPath && abs(gradient) > maxGradient) return null
        // Slope distance, not map distance.
        val walked = distance * sqrt(1.0 + gradient * gradient)
        var seconds = walked / toblerSpeed(gradient)
        if (!onPath) seconds *= offTrailFactor
        if (grid.hasFlag(to, CellFlags.WETLAND)) seconds *= wetlandFactor
        if (grid.isWaterwayCrossing(to)) {
            seconds *= inWaterwayFactor
            if (!grid.isWaterwayCrossing(from)) {
                seconds += if (grid.hasFlag(to, CellFlags.RIVER)) riverCrossingSeconds else streamCrossingSeconds
            }
        }
        return seconds
    }
}
