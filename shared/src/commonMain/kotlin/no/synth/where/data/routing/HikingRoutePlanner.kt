package no.synth.where.data.routing

import no.synth.where.data.geo.LatLng

sealed interface PlanResult {
    /** [featuresIncomplete]: some lake/river/path tiles were unavailable, so water may be missed. */
    data class Found(val route: TerrainRoute, val featuresIncomplete: Boolean) : PlanResult
    /** Elevation tiles missing (offline) or the area is too large. */
    data object NoTerrainData : PlanResult
    data object NoRoute : PlanResult
}

object HikingRoutePlanner {
    suspend fun plan(
        start: LatLng,
        end: LatLng,
        cost: HikingCost = HikingCost(),
        onProgress: (Float) -> Unit = {},
    ): PlanResult {
        val grid = TerrainGridLoader.load(start, end) ?: return PlanResult.NoTerrainData
        val missing = VectorFeatureLoader.burnInto(grid)
        return when (val r = TerrainRouter.route(grid, start, end, cost, onProgress)) {
            is RouteResult.Found -> PlanResult.Found(r.route, featuresIncomplete = missing > 0)
            RouteResult.OutsideGrid -> PlanResult.NoTerrainData
            RouteResult.NoRoute -> PlanResult.NoRoute
        }
    }
}
