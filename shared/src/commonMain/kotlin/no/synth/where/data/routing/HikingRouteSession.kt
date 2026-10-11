package no.synth.where.data.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

sealed interface RoutePlanEvent {
    data class Planned(
        val distanceMeters: Double,
        val durationSeconds: Double,
        val crossings: Int,
        val featuresIncomplete: Boolean,
    ) : RoutePlanEvent
    /** Elevation tiles missing (offline) or the area is too large. */
    data object NoTerrainData : RoutePlanEvent
    data object NoRoute : RoutePlanEvent
}

/** Crossings of the planned route saved as track [trackId]. Not persisted with the track. */
data class PlannedCrossings(val trackId: String, val crossings: List<RiverCrossing>)

/**
 * Plans hiking routes through ruler points and saves them as tracks. One per app, on an app-wide
 * [scope], so leaving the map screen doesn't cancel a plan in progress.
 */
class HikingRouteSession(
    private val trackRepository: TrackRepository,
    private val scope: CoroutineScope,
) {
    /** Progress 0..1 while planning, null when idle. */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val _crossings = MutableStateFlow<PlannedCrossings?>(null)
    val crossings: StateFlow<PlannedCrossings?> = _crossings.asStateFlow()

    private val _events = Channel<RoutePlanEvent>(Channel.BUFFERED)
    val events: Flow<RoutePlanEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    /** Routes each leg between consecutive [waypoints]; calls [onPlanned] after the track is saved. */
    fun plan(waypoints: List<LatLng>, onPlanned: () -> Unit = {}) {
        if (waypoints.size < 2 || job?.isActive == true) return
        job = scope.launch {
            try {
                _progress.value = 0f
                _events.send(planLegs(waypoints, onPlanned))
            } finally {
                _progress.value = null
            }
        }
    }

    private suspend fun planLegs(waypoints: List<LatLng>, onPlanned: () -> Unit): RoutePlanEvent {
        val legs = waypoints.zipWithNext()
        val routes = mutableListOf<TerrainRoute>()
        var incomplete = false
        for ((i, leg) in legs.withIndex()) {
            val grid = TerrainGridLoader.load(leg.first, leg.second) ?: return RoutePlanEvent.NoTerrainData
            incomplete = VectorFeatureLoader.burnInto(grid) > 0 || incomplete
            routes += TerrainRouter.route(grid, leg.first, leg.second) { p -> _progress.value = (i + p) / legs.size }
                ?: return RoutePlanEvent.NoRoute
        }
        // Each leg starts where the previous ended; drop the duplicated joint.
        val points = routes.flatMapIndexed { i, r -> if (i == 0) r.points else r.points.drop(1) }
        val crossings = routes.flatMap { it.crossings }
        val name = GeocodingHelper.routeName(waypoints.first(), waypoints.last()) ?: getString(Res.string.hiking_route)
        val track = trackRepository.savePlannedRoute(name, points)
        _crossings.value = PlannedCrossings(track.id, crossings)
        onPlanned()
        return RoutePlanEvent.Planned(
            distanceMeters = routes.sumOf { it.distanceMeters },
            durationSeconds = routes.sumOf { it.durationSeconds },
            crossings = crossings.size,
            featuresIncomplete = incomplete,
        )
    }
}
