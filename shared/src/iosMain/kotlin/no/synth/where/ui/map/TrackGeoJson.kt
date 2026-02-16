package no.synth.where.ui.map

import no.synth.where.data.TrackPoint

fun buildTrackGeoJson(points: List<TrackPoint>): String {
    val coordinates = points.joinToString(",") { point ->
        "[${point.latLng.longitude},${point.latLng.latitude}]"
    }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}
