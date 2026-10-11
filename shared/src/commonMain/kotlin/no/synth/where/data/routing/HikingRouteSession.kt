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
    data object NoTerrainData : RoutePlanEvent
    data object NoRoute : RoutePlanEvent
}

/** Plans a hiking route through the ruler points and saves it as a track. One per map screen. */
class HikingRouteSession(
    private val trackRepository: TrackRepository,
    private val scope: CoroutineScope,
) {
    /** Progress 0..1 while planning, null when idle. */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    /** Crossings of the last planned route, keyed by its track id so they hide with the track. */
    private val _crossings = MutableStateFlow<Pair<String, List<RiverCrossing>>?>(null)
    val crossings: StateFlow<Pair<String, List<RiverCrossing>>?> = _crossings.asStateFlow()

    private val _events = Channel<RoutePlanEvent>(Channel.BUFFERED)
    val events: Flow<RoutePlanEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    /** Routes each leg between consecutive [waypoints]; calls [onPlanned] after the track is saved. */
    fun plan(waypoints: List<LatLng>, onPlanned: () -> Unit = {}) {
        if (waypoints.size < 2 || job?.isActive == true) return
        job = scope.launch {
            try {
                val legs = waypoints.zipWithNext()
                val routes = mutableListOf<TerrainRoute>()
                var incomplete = false
                _progress.value = 0f
                for ((i, leg) in legs.withIndex()) {
                    val result = HikingRoutePlanner.plan(leg.first, leg.second) { p ->
                        _progress.value = (i + p) / legs.size
                    }
                    when (result) {
                        is PlanResult.Found -> {
                            routes += result.route
                            incomplete = incomplete || result.featuresIncomplete
                        }
                        PlanResult.NoTerrainData -> return@launch _events.send(RoutePlanEvent.NoTerrainData)
                        PlanResult.NoRoute -> return@launch _events.send(RoutePlanEvent.NoRoute)
                    }
                }
                // Each leg starts where the previous ended; drop the duplicated joint.
                val points = routes.flatMapIndexed { i, r -> if (i == 0) r.points else r.points.drop(1) }
                val crossings = routes.flatMap { it.crossings }
                val track = trackRepository.savePlannedRoute(routeName(waypoints.first(), waypoints.last()), points)
                _crossings.value = track.id to crossings
                onPlanned()
                _events.send(
                    RoutePlanEvent.Planned(
                        distanceMeters = routes.sumOf { it.distanceMeters },
                        durationSeconds = routes.sumOf { it.durationSeconds },
                        crossings = crossings.size,
                        featuresIncomplete = incomplete,
                    )
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun routeName(start: LatLng, end: LatLng): String {
        val from = GeocodingHelper.reverseGeocode(start)
        val to = GeocodingHelper.reverseGeocode(end)
        return when {
            from != null && to != null && from != to -> "$from → $to"
            from != null -> from
            else -> getString(Res.string.hiking_route)
        }
    }
}
