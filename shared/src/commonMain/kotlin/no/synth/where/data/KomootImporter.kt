package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

class KomootImporter(
    private val client: HttpClient = createDefaultHttpClient()
) {

    suspend fun importFromUrl(
        input: String,
        addElevation: Boolean = true
    ): Track? {
        val ref = parseKomootUrl(input)
        if (ref == null) {
            Logger.e("Could not parse Komoot URL: $input")
            return null
        }

        val tourData = fetchTourData(ref)
        if (tourData == null) {
            Logger.e("Could not extract tour data from Komoot tour ${ref.id}")
            return null
        }

        if (tourData.points.isEmpty()) {
            Logger.e("No coordinates found for Komoot tour ${ref.id}")
            return null
        }
        Logger.d("Extracted ${tourData.points.size} points from Komoot tour ${ref.id}")

        val hasKomootAltitude = tourData.points.any { it.altitude != null }
        val now = currentTimeMillis()
        val trackPoints = if (hasKomootAltitude) {
            tourData.points.map { pt ->
                TrackPoint(
                    latLng = LatLng(pt.lat, pt.lng),
                    timestamp = pt.timestamp ?: now,
                    altitude = pt.altitude
                )
            }
        } else if (addElevation) {
            ImporterUtils.enrichWithElevation(client, tourData.points.map { LatLng(it.lat, it.lng) })
        } else {
            tourData.points.map {
                TrackPoint(latLng = LatLng(it.lat, it.lng), timestamp = it.timestamp ?: now)
            }
        }

        val fallbackName = "Komoot Tour ${ref.id}"
        return Track(
            name = tourData.name ?: fallbackName,
            points = trackPoints,
            startTime = trackPoints.firstOrNull()?.timestamp ?: now,
            endTime = trackPoints.lastOrNull()?.timestamp ?: now,
            isRecording = false
        )
    }

    private data class TourData(
        val points: List<KomootPoint>,
        val name: String?
    )

    private suspend fun fetchTourData(ref: KomootRef): TourData? {
        return try {
            val coordsText = client.get("https://api.komoot.de/v007/tours/${ref.id}/coordinates").bodyAsText()
            val coordsJson = lenientJson.parseToJsonElement(coordsText).jsonObject
            val items = coordsJson["items"]?.jsonArray ?: return null
            val points = parseCoordinateItems(items)
            if (points.isEmpty()) return null

            val name = try {
                val tourText = client.get("https://api.komoot.de/v007/tours/${ref.id}").bodyAsText()
                lenientJson.parseToJsonElement(tourText).jsonObject["name"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                null
            }

            TourData(points = points, name = name)
        } catch (e: Exception) {
            Logger.d("Komoot API endpoint failed: ${e.message}")
            null
        }
    }

    private fun parseCoordinateItems(items: JsonArray): List<KomootPoint> {
        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lng = obj["lng"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val alt = obj["alt"]?.jsonPrimitive?.doubleOrNull
            val t = obj["t"]?.jsonPrimitive?.longOrNull
            KomootPoint(lat = lat, lng = lng, altitude = alt, timestamp = t)
        }
    }

    data class KomootRef(val id: String)

    data class KomootPoint(
        val lat: Double,
        val lng: Double,
        val altitude: Double?,
        val timestamp: Long?
    )

    companion object {

        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseKomootUrl(input: String): KomootRef? {
            val trimmed = input.trim()

            val tourPattern = Regex("""komoot\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?tour/(\d+)""")
            tourPattern.find(trimmed)?.let { return KomootRef(id = it.groupValues[1]) }

            val dePattern = Regex("""komoot\.de/(?:[a-z]{2}(?:-[a-z]{2})?/)?tour/(\d+)""")
            dePattern.find(trimmed)?.let { return KomootRef(id = it.groupValues[1]) }

            return null
        }

        fun extractName(html: String): String? {
            val jsonName = ImporterUtils.extractJsonName(html)
            if (jsonName != null) return jsonName

            val ogTitle = ImporterUtils.extractOgTitle(html)
            if (ogTitle != null) return ImporterUtils.cleanTitleSuffix(ogTitle, "komoot", "Komoot")

            val title = ImporterUtils.extractTitleTag(html)
            if (title != null) return ImporterUtils.cleanTitleSuffix(title, "komoot", "Komoot")

            return null
        }

        fun extractCoordinatesFromHtml(html: String): List<KomootPoint>? {
            val pattern = Regex(""""items"\s*:\s*\[\s*\{\s*"lat"\s*:""")
            val match = pattern.find(html) ?: return null
            val startIdx = match.range.first + match.value.indexOf('[')

            var depth = 0
            var endIdx = startIdx
            while (endIdx < html.length) {
                when (html[endIdx]) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                endIdx++
            }
            if (depth != 0) return null

            val arrayContent = html.substring(startIdx, endIdx + 1)
            return parseCoordinateArray(arrayContent)
        }

        fun parseCoordinateArray(json: String): List<KomootPoint>? {
            val points = mutableListOf<KomootPoint>()

            val objectPattern = Regex("""\{[^}]*"lat"\s*:\s*(-?\d+\.?\d*)[^}]*"lng"\s*:\s*(-?\d+\.?\d*)[^}]*\}""")
            for (objMatch in objectPattern.findAll(json)) {
                val fullObj = objMatch.value
                val lat = objMatch.groupValues[1].toDoubleOrNull() ?: continue
                val lng = objMatch.groupValues[2].toDoubleOrNull() ?: continue
                val alt = Regex(""""alt"\s*:\s*(-?\d+\.?\d*)""")
                    .find(fullObj)?.groupValues?.get(1)?.toDoubleOrNull()
                val t = Regex(""""t"\s*:\s*(\d+)""")
                    .find(fullObj)?.groupValues?.get(1)?.toLongOrNull()
                points.add(KomootPoint(lat = lat, lng = lng, altitude = alt, timestamp = t))
            }

            return points.ifEmpty { null }
        }

        fun extractEncodedPolyline(html: String): String? {
            val patterns = listOf(
                Regex(""""encodedPolyline"\s*:\s*"([^"]+)""""),
                Regex(""""polyline"\s*:\s*"([^"]+)""""),
            )
            for (p in patterns) {
                val m = p.find(html)
                if (m != null) return ImporterUtils.unescapeJsonString(m.groupValues[1])
            }
            return null
        }
    }
}
