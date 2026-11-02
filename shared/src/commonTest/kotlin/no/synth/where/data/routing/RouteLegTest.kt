package no.synth.where.data.routing

import kotlinx.coroutines.test.runTest
import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RouteLegTest {
    private val grid = TerrainGrid(40, 40, 13, 1_100_000, 600_000, 10.0, FloatArray(40 * 40) { 100f })
    private val from = grid.cellToLatLng(5, 5)
    private val to = grid.cellToLatLng(35, 35)
    private val cliff = LatLng(60.0, 10.0)
    private val area = LegArea(grid, featuresIncomplete = false)

    private suspend fun leg(
        load: suspend (Double) -> LegArea? = { area },
        refine: suspend (TerrainGrid, TerrainRoute) -> CliffMap.Result? = { _, _ -> null },
    ) = RouteLeg.route(from, to, load, refine)

    @Test
    fun noCliffDataKeepsTheFirstRoute() = runTest {
        val leg = assertIs<LegResult.Found>(leg()).leg
        assertTrue(leg.cliffs.isEmpty())
        assertTrue(!leg.cliffsIncomplete)
    }

    @Test
    fun climbingRouteTriesTheWiderAreaFirst() = runTest {
        val scales = mutableListOf<Double>()
        val result = leg(
            load = { scales += it; area },
            refine = { _, r -> CliffMap.Result(r, cliffs = if (scales.size == 1) listOf(cliff) else emptyList(), complete = true) },
        )
        assertEquals(RouteLeg.MARGIN_SCALES, scales)
        assertTrue(assertIs<LegResult.Found>(result).leg.cliffs.isEmpty())
    }

    @Test
    fun climbingRouteIsKeptWhenTheWiderAreaHasNoWayAroundEither() = runTest {
        val result = leg(refine = { _, r -> CliffMap.Result(r, listOf(cliff), complete = false) })
        val leg = assertIs<LegResult.Found>(result).leg
        assertEquals(listOf(cliff), leg.cliffs)
        assertTrue(leg.cliffsIncomplete)
    }

    @Test
    fun climbingRouteIsKeptWhenTheWiderAreaCannotLoad() = runTest {
        val result = leg(
            load = { if (it == RouteLeg.MARGIN_SCALES.first()) area else null },
            refine = { _, r -> CliffMap.Result(r, listOf(cliff), complete = true) },
        )
        assertEquals(listOf(cliff), assertIs<LegResult.Found>(result).leg.cliffs)
    }

    @Test
    fun missingTerrainAndNoRouteAreReported() = runTest {
        assertSame(RoutePlanEvent.NoTerrainData, assertIs<LegResult.Failed>(leg(load = { null })).event)
        val walledIn = TerrainGrid(40, 40, 13, 1_100_000, 600_000, 10.0, FloatArray(40 * 40) { 100f })
        for (x in 0 until 40) walledIn.addFlag(walledIn.index(x, 20), CellFlags.WATER)
        assertSame(RoutePlanEvent.NoRoute, assertIs<LegResult.Failed>(leg(load = { LegArea(walledIn, false) })).event)
    }
}
