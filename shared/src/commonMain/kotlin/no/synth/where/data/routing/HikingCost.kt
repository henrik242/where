package no.synth.where.data.routing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tan

/** Walking time for a step between grid cells, in seconds. */
internal object HikingCost {
    /** Cells steeper than this, or steps rising/falling faster, are impassable (cliff proxy). */
    private val maxGradient = tan(40.0 * PI / 180.0)
    /** Tobler's off-path factor: off-trail walking takes 1/0.6 of on-trail time. */
    private const val OFF_TRAIL_FACTOR = 1.0 / 0.6
    private const val WETLAND_FACTOR = 1.5
    private const val STREAM_CROSSING_SECONDS = 60.0
    private const val RIVER_CROSSING_SECONDS = 900.0
    /** Discourages walking along inside a stream or river. */
    private const val IN_WATERWAY_FACTOR = 3.0
    /**
     * Off-path descents steeper than this cost up to [STEEP_DESCENT_MAX_FACTOR] times more at
     * [maxGradient]: Tobler rates them faster than the same climb, but they're where hikers get stuck.
     */
    private val steepDescent = tan(25.0 * PI / 180.0)
    private const val STEEP_DESCENT_MAX_FACTOR = 3.0
    /** Per cliff cell, when no route avoids cliffs: picks the route over the fewest. */
    const val CLIFF_PENALTY_SECONDS = 1800.0

    /** Fastest possible speed in m/s, for an admissible A* heuristic. */
    val maxSpeed = toblerSpeed(-0.05)

    /** Tobler's hiking function: speed in m/s over horizontal distance, for gradient dh/dx. */
    private fun toblerSpeed(gradient: Double) = 6.0 * exp(-3.5 * abs(gradient + 0.05)) / 3.6

    // Table lookup instead of exp() per step (~15% of search time). Linear interpolation keeps the
    // relative error near 1e-7, below the Float precision of A*'s g scores.
    private const val TOBLER_STEPS = 4096
    private val toblerMaxU = maxGradient + 0.05
    private val toblerScale = TOBLER_STEPS / toblerMaxU
    private val toblerTable = DoubleArray(TOBLER_STEPS + 2) { toblerSpeed(it / toblerScale - 0.05) }

    private fun toblerSpeedFast(gradient: Double): Double {
        val u = abs(gradient + 0.05)
        // Steeper steps are only allowed along paths; compute those exactly.
        if (u >= toblerMaxU) return toblerSpeed(gradient)
        val f = u * toblerScale
        val i = f.toInt()
        val t = f - i
        return toblerTable[i] + (toblerTable[i + 1] - toblerTable[i]) * t
    }

    private fun TerrainGrid.isPath(i: Int) = hasFlag(i, CellFlags.PATH or CellFlags.BRIDGE)

    /** A step into or out of a cliff cell, other than along a path; see [stepSeconds]. */
    fun isCliffStep(grid: TerrainGrid, from: Int, to: Int) =
        (grid.hasFlag(from, CellFlags.CLIFF) || grid.hasFlag(to, CellFlags.CLIFF)) && !(grid.isPath(from) && grid.isPath(to))

    /** Cells too steep to walk (central differences), plus open water, glaciers and, unless [cliffsPassable], cliffs. */
    fun blockedCells(grid: TerrainGrid, cliffsPassable: Boolean = false): BooleanArray {
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
                if (grid.isPath(i)) continue
                if (grid.hasFlag(i, CellFlags.CLIFF) && !cliffsPassable) {
                    blocked[i] = true
                    continue
                }
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
     * Seconds to walk [distance] meters from cell [from] to adjacent cell [to], or null if impassable.
     * A cliff step costs [cliffPenalty], or is impassable when that is null.
     */
    fun stepSeconds(grid: TerrainGrid, from: Int, to: Int, distance: Double, cliffPenalty: Double? = null): Double? {
        val gradient = (grid.elevations[to] - grid.elevations[from]) / distance
        // Only a step along the path is exempt, not one dropping onto it from a cut or cliff above.
        val onPath = grid.isPath(from) && grid.isPath(to)
        if (!onPath && abs(gradient) > maxGradient) return null
        val cliffSeconds = if (isCliffStep(grid, from, to)) cliffPenalty ?: return null else 0.0
        var seconds = distance / toblerSpeedFast(gradient)
        if (!onPath) {
            seconds *= OFF_TRAIL_FACTOR
            if (-gradient > steepDescent) {
                seconds *= 1 + (STEEP_DESCENT_MAX_FACTOR - 1) * (-gradient - steepDescent) / (maxGradient - steepDescent)
            }
        }
        if (grid.hasFlag(to, CellFlags.WETLAND)) seconds *= WETLAND_FACTOR
        if (grid.isWaterwayCrossing(to)) {
            seconds *= IN_WATERWAY_FACTOR
            if (grid.entersCrossing(from, to)) {
                seconds += if (grid.hasFlag(to, CellFlags.RIVER)) RIVER_CROSSING_SECONDS else STREAM_CROSSING_SECONDS
            }
        }
        return seconds + cliffSeconds
    }
}
