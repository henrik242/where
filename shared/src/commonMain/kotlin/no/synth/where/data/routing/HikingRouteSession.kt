package no.synth.where.data.routing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import no.synth.where.data.TrackRepository
import no.synth.where.data.geo.LatLng
import no.synth.where.resources.Res
import no.synth.where.resources.hiking_route
import org.jetbrains.compose.resources.getString

/** Why a plan found no route. */
sealed interface RoutePlanEvent {
    /** Elevation tiles missing (offline) or the area is too large. */
    data object NoTerrainData : RoutePlanEvent
    data object NoRoute : RoutePlanEvent
}

/** Crossings of the planned route saved as track [trackId]. Not persisted with the track. */
data class PlannedCrossings(val trackId: String, val crossings: List<RiverCrossing>)

/** A planned route not saved yet. */
data class PlannedRoute(
    val points: List<RoutePoint>,
    val crossings: List<RiverCrossing>,
    val name: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val featuresIncomplete: Boolean,
)

/**
 * Plans hiking routes through ruler points as a [preview] the user can save as a track. One per
 * app, on an app-wide [scope], so a plan or save isn't cut short by the screen being recreated.
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

    private companion object {
        val MARGIN_SCALES = listOf(1.0, 4.0)
    }

    /** Routes each leg between consecutive [waypoints] into [preview]. */
    fun plan(waypoints: List<LatLng>) {
        if (waypoints.size < 2 || job?.isActive == true) return
        val previous = job
        _preview.value = null
        job = scope.launch {
            // A cancelled plan may still be unwinding; let it reset isPlanning before this one sets it.
            previous?.join()
            try {
                _isPlanning.value = true
                planLegs(waypoints)?.let { _events.send(it) }
            } finally {
                _isPlanning.value = false
            }
        }
    }

    /** Stops a plan in progress and drops the preview. */
    fun clear() {
        job?.cancel()
        _preview.value = null
    }

    /** Saves [route] as track [name] and shows it on the map, then calls [onSaved]. */
    fun save(route: PlannedRoute, name: String, onSaved: () -> Unit) {
        scope.launch {
            val track = trackRepository.savePlannedRoute(name, route.points)
            _crossings.value = PlannedCrossings(track.id, route.crossings)
            onSaved()
        }
    }

    /** Routes the legs into [preview]; returns why not when it can't. */
    private suspend fun planLegs(waypoints: List<LatLng>): RoutePlanEvent? = coroutineScope {
        val legs = waypoints.zipWithNext()
        // Fetching tiles is the slow part: download each leg's tiles while the previous one is routed.
        val prefetched = legs.map { CompletableDeferred<Unit>() }
        launch {
            legs.forEachIndexed { i, (from, to) ->
                try {
                    TerrainGridLoader.prefetch(from, to)
                } finally {
                    prefetched[i].complete(Unit)
                }
            }
        }
        val name = async { GeocodingHelper.routeName(waypoints.first(), waypoints.last()) ?: getString(Res.string.hiking_route) }
        try {
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
                        ?: if (attempt == 0) return@coroutineScope RoutePlanEvent.NoTerrainData else break
                    incomplete = VectorFeatureLoader.burnInto(grid) > 0 || incomplete
                    route = TerrainRouter.route(grid, from, to)
                    if (route != null) break
                }
                routes += route ?: return@coroutineScope RoutePlanEvent.NoRoute
            }
            _preview.value = PlannedRoute(
                // Each leg starts where the previous ended; drop the duplicated joint.
                points = routes.flatMapIndexed { i, r -> if (i == 0) r.points else r.points.drop(1) },
                crossings = routes.flatMap { it.crossings },
                name = name.await(),
                distanceMeters = routes.sumOf { it.distanceMeters },
                durationSeconds = routes.sumOf { it.durationSeconds },
                featuresIncomplete = incomplete,
            )
            null
        } finally {
            coroutineContext.cancelChildren()
        }
    }
}
