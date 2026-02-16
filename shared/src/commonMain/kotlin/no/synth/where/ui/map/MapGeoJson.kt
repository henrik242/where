package no.synth.where.ui.map

import no.synth.where.data.RulerPoint
import no.synth.where.data.SavedPoint
import no.synth.where.data.TrackPoint

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
