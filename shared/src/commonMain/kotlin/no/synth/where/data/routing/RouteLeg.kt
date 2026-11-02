package no.synth.where.data.routing

import no.synth.where.data.geo.LatLng

/** A routed leg; [cliffs] are where it climbs one there was no way around. */
internal class Leg(
    val route: TerrainRoute,
    val featuresIncomplete: Boolean,
    val cliffsIncomplete: Boolean,
    val cliffs: List<LatLng>,
)

/** A leg's grid with lakes, rivers and paths burnt in; [featuresIncomplete] when some of those tiles were missing. */
internal class LegArea(val grid: TerrainGrid, val featuresIncomplete: Boolean)

internal sealed interface LegResult {
    class Found(val leg: Leg) : LegResult
    class Failed(val event: RoutePlanEvent) : LegResult
}

internal object RouteLeg {
    /** The way around a long lake or cliff can lie outside the default area; retry once, wider. */
    val MARGIN_SCALES = listOf(1.0, 4.0)

    /**
     * Routes [from] to [to] on the area [load] gives per margin scale, null when terrain is missing.
     * [refine] checks for cliffs, null where there's no data for that. A route over a cliff is kept
     * only if the wider area has no way around either.
     */
    suspend fun route(
        from: LatLng,
        to: LatLng,
        load: suspend (marginScale: Double) -> LegArea?,
        refine: suspend (TerrainGrid, TerrainRoute) -> CliffMap.Result?,
    ): LegResult {
        var featuresIncomplete = false
        var climbing: Leg? = null
        for ((attempt, marginScale) in MARGIN_SCALES.withIndex()) {
            val area = load(marginScale) ?: if (attempt == 0) return LegResult.Failed(RoutePlanEvent.NoTerrainData) else break
            featuresIncomplete = area.featuresIncomplete || featuresIncomplete
            val found = TerrainRouter.route(area.grid, from, to) ?: continue
            val refined = refine(area.grid, found)
                ?: return LegResult.Found(Leg(found, featuresIncomplete, cliffsIncomplete = false, cliffs = emptyList()))
            val leg = Leg(refined.route, featuresIncomplete, !refined.complete, refined.cliffs)
            if (refined.cliffs.isEmpty()) return LegResult.Found(leg)
            climbing = leg
        }
        return climbing?.let { LegResult.Found(it) } ?: LegResult.Failed(RoutePlanEvent.NoRoute)
    }
}
