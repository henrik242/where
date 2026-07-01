package no.synth.where.ui.map

import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerPoint
import no.synth.where.data.SavedPoint
import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import no.synth.where.data.geo.LatLng
import no.synth.where.data.navigation.NavigationProgress
import no.synth.where.util.formatDistance

fun buildTrackGeoJson(points: List<TrackPoint>): String {
    val coordinates = points.joinToString(",") { point ->
        "[${point.latLng.longitude},${point.latLng.latitude}]"
    }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

fun buildSavedPointsGeoJson(points: List<SavedPoint>): String {
    val features = points.joinToString(",") { point ->
        val name = point.name.replace("\"", "\\\"")
        val color = point.color ?: "#FF5722"
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${point.latLng.longitude},${point.latLng.latitude}]},"properties":{"name":"$name","color":"$color"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

fun buildRulerLineGeoJson(points: List<RulerPoint>): String {
    val coordinates = points.joinToString(",") { point ->
        "[${point.latLng.longitude},${point.latLng.latitude}]"
    }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

fun buildRulerPointsGeoJson(points: List<RulerPoint>): String {
    val features = points.joinToString(",") { point ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${point.latLng.longitude},${point.latLng.latitude}]}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

fun buildMeasurementLineGeoJson(m: TwoFingerMeasurement): String =
    """{"type":"Feature","geometry":{"type":"LineString","coordinates":""" +
        "[[${m.lng1},${m.lat1}],[${m.lng2},${m.lat2}]]}}"

fun buildMeasurementPointsGeoJson(m: TwoFingerMeasurement): String {
    val label = m.distanceMeters.formatDistance().replace("\"", "\\\"")
    return """{"type":"FeatureCollection","features":[""" +
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${m.lng1},${m.lat1}]},"properties":{"role":"endpoint"}},""" +
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${m.lng2},${m.lat2}]},"properties":{"role":"endpoint"}},""" +
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${m.midLng},${m.midLat}]},"properties":{"role":"label","label":"$label"}}""" +
        "]}"
}

/**
 * Split the route at [snapped] on segment [seg] into (completed, remaining) LineStrings.
 * completed = points[0..seg] + snapped ; remaining = snapped + points[seg+1..end]
 */
fun buildRouteSplitGeoJson(
    points: List<TrackPoint>,
    seg: Int,
    snapped: LatLng
): Pair<String, String> {
    fun line(coords: List<LatLng>): String {
        val c = coords.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$c]}}"""
    }
    val done = points.take(seg + 1).map { it.latLng } + snapped
    val remaining = listOf(snapped) + points.drop(seg + 1).map { it.latLng }
    return line(done) to line(remaining)
}

/** Straight connector from the user's location to the nearest point on the route. */
fun buildOffCourseGeoJson(from: LatLng, to: LatLng): String =
    """{"type":"Feature","geometry":{"type":"LineString","coordinates":[""" +
        "[${from.longitude},${from.latitude}],[${to.longitude},${to.latitude}]" +
        """]}}"""

/** GeoJSON for the three navigation map layers. [offCourse] is null when no connector should draw. */
data class NavigationLayers(val completed: String, val remaining: String, val offCourse: String?)

/**
 * Assemble the completed/remaining split and the optional off-course connector for one progress
 * frame. Shared by both platforms so the reversed-points and off-course logic can't drift.
 * The connector is drawn only when off course and not yet arrived.
 */
fun buildNavigationLayers(
    track: Track,
    reversed: Boolean,
    progress: NavigationProgress,
): NavigationLayers {
    val ordered = if (reversed) track.points.asReversed() else track.points
    val (completed, remaining) = if (progress.onCourse) {
        buildRouteSplitGeoJson(ordered, progress.segment, progress.snapped)
    } else {
        // Off course the snap point is unreliable; show the whole route to follow rather than a
        // split that could grey out the entire line (e.g. when the nearest point is the far end).
        buildTrackGeoJson(emptyList()) to buildTrackGeoJson(ordered)
    }
    val offCourse = if (!progress.onCourse && !progress.atEnd) {
        buildOffCourseGeoJson(progress.location, progress.snapped)
    } else {
        null
    }
    return NavigationLayers(completed, remaining, offCourse)
}

fun buildSearchResultsGeoJson(results: List<PlaceSearchClient.SearchResult>): String {
    val features = results.joinToString(",") { result ->
        val name = result.name.replace("\"", "\\\"")
        val type = result.type.replace("\"", "\\\"")
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${result.latLng.longitude},${result.latLng.latitude}]},"properties":{"name":"$name","type":"$type"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
