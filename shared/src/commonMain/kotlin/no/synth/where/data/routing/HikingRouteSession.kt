package no.synth.where.data.routing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.geo.LatLng
import no.synth.where.resources.Res
import no.synth.where.resources.hiking_route
import no.synth.where.util.Logger
import org.jetbrains.compose.resources.getString
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

sealed interface RoutePlanEvent {
    /** The route is in [HikingRouteSession.preview]. */
    data object Planned : RoutePlanEvent
    /** Elevation tiles missing (offline) or the area is too large. */
    data object NoTerrainData : RoutePlanEvent
    data object NoRoute : RoutePlanEvent
}

/** Crossings of the planned route saved as track [trackId]. Not persisted with the track. */
data class PlannedCrossings(val trackId: String, val crossings: List<RiverCrossing>)

/** A planned route not saved yet; [waypoints] are the ruler points it was planned through. */
data class PlannedRoute(
    val waypoints: List<LatLng>,
    val points: List<RoutePoint>,
    val crossings: List<RiverCrossing>,
    val name: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val featuresIncomplete: Boolean,
)

/**
 * Plans hiking routes through ruler points as a [preview] the user can save as a track. One per
 * app, on an app-wide [scope], so leaving the map screen doesn't cancel a plan in progress.
 */
class HikingRouteSession(
    private val trackRepository: TrackRepository,
    private val scope: CoroutineScope,
) {
    // A percentage would mislead: the slow part is fetching tiles, which runs ahead of the search.
    private val _isPlanning = MutableStateFlow(false)
    val isPlanning: StateFlow<Boolean> = _isPlanning.asStateFlow()

    private val _preview = MutableStateFlow<PlannedRoute?>(null)
    val preview: StateFlow<PlannedRoute?> = _preview.asStateFlow()

    private val _crossings = MutableStateFlow<PlannedCrossings?>(null)
    val crossings: StateFlow<PlannedCrossings?> = _crossings.asStateFlow()

    private val _events = Channel<RoutePlanEvent>(Channel.BUFFERED)
    val events: Flow<RoutePlanEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    @Volatile
    private var routeJustSaved = false

    /** True once after a planned route is saved, so the camera keeps the preview's looser framing. */
    fun consumeRouteJustSaved(): Boolean = routeJustSaved.also { routeJustSaved = false }

    private companion object {
        val MARGIN_SCALES = listOf(1.0, 4.0)
    }

    /** Routes each leg between consecutive [waypoints] into [preview]. */
    fun plan(waypoints: List<LatLng>) {
        if (waypoints.size < 2 || job?.isActive == true) return
        _preview.value = null
        job = scope.launch {
            try {
                _isPlanning.value = true
                _events.send(planLegs(waypoints))
            } finally {
                _isPlanning.value = false
            }
        }
    }

    /** Saves [preview] as track [name] and shows it on the map; null when there is nothing to save. */
    suspend fun save(name: String): Track? {
        val route = _preview.value ?: return null
        // Set before saving: adding the track to the map triggers the camera fit.
        routeJustSaved = true
        val track = trackRepository.savePlannedRoute(name, route.points)
        _crossings.value = PlannedCrossings(track.id, route.crossings)
        _preview.value = null
        return track
    }

    fun discard() {
        _preview.value = null
    }

    /** Stops a plan in progress; it reports nothing. */
    fun cancel() {
        job?.cancel()
    }

    private suspend fun planLegs(waypoints: List<LatLng>): RoutePlanEvent = coroutineScope {
        val legs = waypoints.zipWithNext()
        // Fetching tiles is the slow part: download each leg's tiles while the previous one is routed.
        val prefetched = legs.map { CompletableDeferred<Unit>() }
        launch {
            legs.forEachIndexed { i, (from, to) ->
                try {
                    TerrainGridLoader.prefetch(from, to)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e, "Failed to prefetch route tiles")
                } finally {
                    prefetched[i].complete(Unit)
                }
            }
        }
        val name = async { GeocodingHelper.routeName(waypoints.first(), waypoints.last()) ?: getString(Res.string.hiking_route) }
        try {
            planPrefetchedLegs(waypoints, legs, prefetched, name)
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    private suspend fun planPrefetchedLegs(
        waypoints: List<LatLng>,
        legs: List<Pair<LatLng, LatLng>>,
        prefetched: List<CompletableDeferred<Unit>>,
        name: Deferred<String>,
    ): RoutePlanEvent {
        val routes = mutableListOf<TerrainRoute>()
        var incomplete = false
        for ((i, leg) in legs.withIndex()) {
            val (from, to) = leg
            prefetched[i].await()
            // The way around a long lake can lie outside the default area; retry once, wider.
            var route: TerrainRoute? = null
            for ((attempt, marginScale) in MARGIN_SCALES.withIndex()) {
                if (attempt > 0) TerrainGridLoader.prefetch(from, to, marginScale)
                val grid = TerrainGridLoader.load(from, to, marginScale)
                    ?: if (attempt == 0) return RoutePlanEvent.NoTerrainData else break
                incomplete = VectorFeatureLoader.burnInto(grid) > 0 || incomplete
                route = TerrainRouter.route(grid, from, to)
                if (route != null) break
            }
            routes += route ?: return RoutePlanEvent.NoRoute
        }
        // Each leg starts where the previous ended; drop the duplicated joint.
        val points = routes.flatMapIndexed { i, r -> if (i == 0) r.points else r.points.drop(1) }
        val crossings = routes.flatMap { it.crossings }
        _preview.value = PlannedRoute(
            waypoints = waypoints,
            points = points,
            crossings = crossings,
            name = name.await(),
            distanceMeters = routes.sumOf { it.distanceMeters },
            durationSeconds = routes.sumOf { it.durationSeconds },
            featuresIncomplete = incomplete,
        )
        return RoutePlanEvent.Planned
    }
}
