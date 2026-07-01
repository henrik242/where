package no.synth.where.ui.map

import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerPoint
import no.synth.where.data.SavedPoint
import no.synth.where.data.TrackPoint
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

fun buildSearchResultsGeoJson(results: List<PlaceSearchClient.SearchResult>): String {
    val features = results.joinToString(",") { result ->
        val name = result.name.replace("\"", "\\\"")
        val type = result.type.replace("\"", "\\\"")
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${result.latLng.longitude},${result.latLng.latitude}]},"properties":{"name":"$name","type":"$type"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
