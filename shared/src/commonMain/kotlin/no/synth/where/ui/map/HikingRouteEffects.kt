package no.synth.where.ui.map

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import no.synth.where.data.Track
import no.synth.where.data.routing.HikingRouteSession
import no.synth.where.data.routing.PlannedCrossings
import no.synth.where.data.routing.RoutePlanEvent
import no.synth.where.resources.Res
import no.synth.where.resources.route_crossings
import no.synth.where.resources.route_duration_hours
import no.synth.where.resources.route_duration_minutes
import no.synth.where.resources.route_features_incomplete
import no.synth.where.resources.route_no_terrain
import no.synth.where.resources.route_not_found
import no.synth.where.resources.route_planned
import no.synth.where.util.formatDistance
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.math.roundToInt

/** Shows each planning result from [session] as a snackbar. */
@Composable
fun HikingRouteSnackbars(session: HikingRouteSession, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(session) {
        session.events.collect { event ->
            val message = when (event) {
                is RoutePlanEvent.Planned -> buildList {
                    add(getString(Res.string.route_planned, event.distanceMeters.formatDistance(), formatHikingTime(event.durationSeconds)))
                    if (event.crossings > 0) add(getPluralString(Res.plurals.route_crossings, event.crossings, event.crossings))
                    if (event.featuresIncomplete) add(getString(Res.string.route_features_incomplete))
                }.joinToString(". ")
                RoutePlanEvent.NoTerrainData -> getString(Res.string.route_no_terrain)
                RoutePlanEvent.NoRoute -> getString(Res.string.route_not_found)
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }
}

/** Walking time rounded to 5 minutes; finer would suggest accuracy the model doesn't have. */
private suspend fun formatHikingTime(seconds: Double): String {
    val minutes = ((seconds / 60.0 / 5.0).roundToInt() * 5).coerceAtLeast(5)
    return if (minutes < 60) {
        getString(Res.string.route_duration_minutes, minutes)
    } else {
        getString(Res.string.route_duration_hours, minutes / 60, minutes % 60)
    }
}

/** Crossing markers for the planned route, only while its track is on the map. */
fun crossingsGeoJsonFor(planned: PlannedCrossings?, viewingTracks: List<Track>): String? {
    if (planned == null || planned.crossings.isEmpty() || viewingTracks.none { it.id == planned.trackId }) return null
    return buildCrossingsGeoJson(planned.crossings)
}
