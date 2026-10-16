package no.synth.where.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import kotlin.time.Instant

/**
 * Reads GeoJSON as tracks and points. All lines are joined into one track, like a multi-`<trk>`
 * GPX; points are waypoints, and become the track when the file has no lines.
 */
object GeoJsonImport {

    private class Feature(val geometry: JsonObject, val properties: JsonObject?)

    fun parseTrack(text: String): Track? = try {
        val root = Json.parseToJsonElement(text).jsonObject
        val features = features(root)
        val lineFeatures = features.filter { it.geometry.type in LINE_TYPES }
        val trackFeatures = lineFeatures.ifEmpty { features.filter { it.geometry.type in POINT_TYPES } }
        val points = trackFeatures.flatMap { feature ->
            val time = feature.properties?.time()
            positions(feature.geometry).mapNotNull { it.toTrackPoint(time) }
        }
        // Feature names first: ogr2ogr writes the layer name ("tracks") at the top level.
        val name = trackFeatures.firstNotNullOfOrNull { it.properties?.string("name") }
            ?: root.string("name")
            ?: Track.DEFAULT_IMPORT_NAME
        Track.fromImportedPoints(name, points)
    } catch (e: Exception) {
        Logger.e(e, "Error parsing GeoJSON track")
        null
    }

    fun parseWaypoints(text: String): List<ParsedWaypoint> = try {
        val root = Json.parseToJsonElement(text).jsonObject
        features(root).filter { it.geometry.type in POINT_TYPES }.flatMap { feature ->
            val props = feature.properties
            positions(feature.geometry).mapNotNull { position ->
                ParsedWaypoint(
                    name = props?.string("name") ?: GpxWaypoints.DEFAULT_NAME,
                    latLng = position.latLngOrNull() ?: return@mapNotNull null,
                    description = props?.string("description").orEmpty(),
                    timestamp = props?.time()
                )
            }
        }
    } catch (e: Exception) {
        Logger.e(e, "Error parsing GeoJSON waypoints")
        emptyList()
    }

    private fun features(root: JsonObject): List<Feature> = when (root.type) {
        "FeatureCollection" -> root["features"].arrayOrEmpty().mapNotNull { (it as? JsonObject)?.toFeature() }
        "Feature" -> listOfNotNull(root.toFeature())
        else -> listOf(Feature(root, null))
    }

    private fun JsonObject.toFeature(): Feature? =
        (this["geometry"] as? JsonObject)?.let { Feature(it, this["properties"] as? JsonObject) }

    private fun positions(geometry: JsonObject): List<JsonElement> {
        val coordinates = geometry["coordinates"] ?: return emptyList()
        return when (geometry.type) {
            "Point" -> listOf(coordinates)
            "LineString", "MultiPoint" -> coordinates.arrayOrEmpty()
            "MultiLineString" -> coordinates.arrayOrEmpty().flatMap { it.arrayOrEmpty() }
            else -> emptyList()
        }
    }

    // GeoJSON positions are [longitude, latitude, elevation?].
    private fun JsonElement.latLngOrNull(): LatLng? {
        val position = this as? JsonArray ?: return null
        val lon = position.numberAt(0) ?: return null
        val lat = position.numberAt(1) ?: return null
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        return LatLng(lat, lon)
    }

    private fun JsonElement.toTrackPoint(time: Long?): TrackPoint? {
        val latLng = latLngOrNull() ?: return null
        return TrackPoint(latLng = latLng, timestamp = time ?: 0L, altitude = (this as? JsonArray)?.numberAt(2))
    }

    private fun JsonArray.numberAt(index: Int): Double? = (getOrNull(index) as? JsonPrimitive)?.doubleOrNull

    private val JsonObject.type: String? get() = (this["type"] as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }

    private fun JsonObject.time(): Long? =
        string("time")?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

    private val LINE_TYPES = setOf("LineString", "MultiLineString")
    private val POINT_TYPES = setOf("Point", "MultiPoint")
}
