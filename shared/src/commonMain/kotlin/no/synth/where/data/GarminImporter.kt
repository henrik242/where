package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

class GarminImporter(
    private val client: HttpClient = createDefaultHttpClient()
) {

    suspend fun importFromUrl(
        input: String,
        addElevation: Boolean = true
    ): Track? {
        val ref = parseGarminUrl(input)
        if (ref == null) {
            Logger.e("Could not parse Garmin URL: $input")
            return null
        }

        val pageData = fetchPageData(ref)
        if (pageData == null) {
            Logger.e("Could not extract track data from Garmin ${ref.type} ${ref.id}")
            return null
        }

        if (pageData.points.isEmpty()) {
            Logger.e("No coordinates found for Garmin ${ref.type} ${ref.id}")
            return null
        }
        Logger.d("Extracted ${pageData.points.size} points from Garmin ${ref.type} ${ref.id}")

        val hasGarminAltitude = pageData.altitudes.any { it != null }
        val trackPoints = if (hasGarminAltitude) {
            val now = currentTimeMillis()
            pageData.points.mapIndexed { i, latLng ->
                TrackPoint(
                    latLng = latLng,
                    timestamp = now,
                    altitude = pageData.altitudes.getOrNull(i)
                )
            }
        } else if (addElevation) {
            ImporterUtils.enrichWithElevation(client, pageData.points)
        } else {
            pageData.points.map { TrackPoint(latLng = it, timestamp = currentTimeMillis()) }
        }

        val label = ref.type.replaceFirstChar { it.uppercase() }
        val fallbackName = "Garmin $label ${ref.id}"
        val now = currentTimeMillis()
        return Track(
            name = pageData.name ?: fallbackName,
            points = trackPoints,
            startTime = now,
            endTime = now,
            isRecording = false
        )
    }

    private data class PageData(
        val points: List<LatLng>,
        val altitudes: List<Double?>,
        val name: String?
    )

    private suspend fun fetchPageData(ref: GarminRef): PageData? {
        return tryApiEndpoint(ref)
    }

    private data class GarminSession(val csrfToken: String, val cookieHeader: String)

    private suspend fun getSession(ref: GarminRef): GarminSession? {
        val pageUrl = "https://connect.garmin.com/modern/${ref.type}/${ref.id}"
        val pageResponse = client.get(pageUrl) {
            header("User-Agent", "Mozilla/5.0")
        }
        val pageHtml = pageResponse.bodyAsText()
        val csrfToken = Regex("""<meta name="csrf-token" content="([^"]+)"""")
            .find(pageHtml)?.groupValues?.get(1) ?: return null
        val cookieHeader = pageResponse.headers.getAll("set-cookie")
            ?.joinToString("; ") { it.substringBefore(";") } ?: ""
        return GarminSession(csrfToken, cookieHeader)
    }

    private suspend fun tryApiEndpoint(ref: GarminRef): PageData? {
        return try {
            val session = getSession(ref) ?: return null

            val servicePath = if (ref.type == "course") "course-service/course" else "activity-service/activity"
            val apiUrl = "https://connect.garmin.com/gc-api/$servicePath/${ref.id}"
            val responseText = client.get(apiUrl) {
                header("User-Agent", "Mozilla/5.0")
                header("connect-csrf-token", session.csrfToken)
                if (session.cookieHeader.isNotEmpty()) header("Cookie", session.cookieHeader)
            }.bodyAsText()

            val json = lenientJson.parseToJsonElement(responseText).jsonObject
            val name = json["courseName"]?.jsonPrimitive?.content
                ?: json["activityName"]?.jsonPrimitive?.content

            // geoPoints: flat lat/lon/elevation array present in course responses
            val geoPoints = json["geoPoints"]?.jsonArray
            if (geoPoints != null && geoPoints.isNotEmpty()) {
                val points = mutableListOf<LatLng>()
                val altitudes = mutableListOf<Double?>()
                for (point in geoPoints) {
                    val obj = point.jsonObject
                    val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: continue
                    val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: continue
                    points.add(LatLng(lat, lon))
                    altitudes.add(obj["elevation"]?.jsonPrimitive?.doubleOrNull)
                }
                if (points.isNotEmpty()) {
                    return PageData(points = points, altitudes = altitudes, name = name)
                }
            }

            // Fallback: geoPolylineDTO for activities
            val polylineArray = json["geoPolylineDTO"]?.jsonObject?.get("polyline")?.jsonArray
                ?: json["polyline"]?.jsonArray
                ?: json["geoPolylineDTO"]?.jsonArray
            if (polylineArray != null && polylineArray.isNotEmpty()) {
                val points = mutableListOf<LatLng>()
                val altitudes = mutableListOf<Double?>()
                for (point in polylineArray) {
                    val obj = point.jsonObject
                    val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
                    val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull ?: continue
                    points.add(LatLng(lat, lon))
                    altitudes.add(obj["altitude"]?.jsonPrimitive?.doubleOrNull)
                }
                if (points.isNotEmpty()) {
                    return PageData(points = points, altitudes = altitudes, name = name)
                }
            }

            // Fallback: activity details endpoint with metrics containing lat/lon
            if (ref.type == "activity") {
                val detailsData = tryDetailsEndpoint(ref, session, name)
                if (detailsData != null) return detailsData
            }

            if (name != null) PageData(points = emptyList(), altitudes = emptyList(), name = name) else null
        } catch (e: Exception) {
            Logger.d("Garmin API endpoint not available: ${e.message}")
            null
        }
    }

    private suspend fun tryDetailsEndpoint(ref: GarminRef, session: GarminSession, name: String?): PageData? {
        return try {
            val apiUrl = "https://connect.garmin.com/gc-api/activity-service/activity/${ref.id}/details"
            val responseText = client.get(apiUrl) {
                header("User-Agent", "Mozilla/5.0")
                header("connect-csrf-token", session.csrfToken)
                if (session.cookieHeader.isNotEmpty()) header("Cookie", session.cookieHeader)
            }.bodyAsText()

            val json = lenientJson.parseToJsonElement(responseText).jsonObject
            val descriptors = json["metricDescriptors"]?.jsonArray ?: return null
            var latIdx = -1
            var lonIdx = -1
            var elevIdx = -1
            for (desc in descriptors) {
                val obj = desc.jsonObject
                val idx = obj["metricsIndex"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                when (obj["key"]?.jsonPrimitive?.content) {
                    "directLatitude" -> latIdx = idx
                    "directLongitude" -> lonIdx = idx
                    "directElevation" -> elevIdx = idx
                }
            }
            if (latIdx == -1 || lonIdx == -1) return null

            val metrics = json["activityDetailMetrics"]?.jsonArray ?: return null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double?>()
            for (entry in metrics) {
                val arr = entry.jsonObject["metrics"]?.jsonArray ?: continue
                val lat = arr.getOrNull(latIdx)?.jsonPrimitive?.doubleOrNull ?: continue
                val lon = arr.getOrNull(lonIdx)?.jsonPrimitive?.doubleOrNull ?: continue
                points.add(LatLng(lat, lon))
                altitudes.add(if (elevIdx >= 0) arr.getOrNull(elevIdx)?.jsonPrimitive?.doubleOrNull else null)
            }
            if (points.isEmpty()) return null
            Logger.d("Extracted ${points.size} points from Garmin details endpoint for ${ref.type} ${ref.id}")
            PageData(points = points, altitudes = altitudes, name = name)
        } catch (e: Exception) {
            Logger.d("Garmin details endpoint not available: ${e.message}")
            null
        }
    }

    data class GarminRef(val type: String, val id: String)

    data class GarminPoint(val lat: Double, val lon: Double, val altitude: Double?) {
        fun toLatLng() = LatLng(lat, lon)
    }

    companion object {
        private val lenientJson = Json { ignoreUnknownKeys = true }

        fun parseGarminUrl(input: String): GarminRef? {
            val trimmed = input.trim()

            val pattern = Regex("""connect\.garmin\.com/(?:modern|app)/(activity|course)/(\d+)""")
            pattern.find(trimmed)?.let { match ->
                return GarminRef(type = match.groupValues[1], id = match.groupValues[2])
            }

            val legacyPattern = Regex("""connect\.garmin\.com/(activity|course)/(\d+)""")
            legacyPattern.find(trimmed)?.let { match ->
                return GarminRef(type = match.groupValues[1], id = match.groupValues[2])
            }

            return null
        }

        fun extractName(html: String): String? {
            val activityName = Regex(""""activityName"\s*:\s*"([^"]{1,200})"""")
                .find(html)?.groupValues?.get(1)
            if (activityName != null) return ImporterUtils.unescapeJsonString(activityName)

            val courseName = Regex(""""courseName"\s*:\s*"([^"]{1,200})"""")
                .find(html)?.groupValues?.get(1)
            if (courseName != null) return ImporterUtils.unescapeJsonString(courseName)

            val ogTitle = ImporterUtils.extractOgTitle(html)
            if (ogTitle != null) return ImporterUtils.cleanTitleSuffix(ogTitle, "Garmin Connect", "Garmin")

            val title = ImporterUtils.extractTitleTag(html)
            if (title != null) return ImporterUtils.cleanTitleSuffix(title, "Garmin Connect", "Garmin")

            return null
        }

        fun extractCoordinatesFromHtml(html: String): List<GarminPoint>? {
            val arrayStart = Regex(""""polyline"\s*:\s*\[\s*\{""").find(html) ?: return null
            val startIdx = arrayStart.range.first + arrayStart.value.indexOf('[')

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
            return parseLatLonArray(arrayContent)
        }

        fun parseLatLonArray(json: String): List<GarminPoint>? {
            val points = mutableListOf<GarminPoint>()

            val objectPattern = Regex("""\{[^}]*"lat"\s*:\s*(-?\d+\.?\d*)[^}]*"lon"\s*:\s*(-?\d+\.?\d*)[^}]*\}""")
            for (match in objectPattern.findAll(json)) {
                val fullObj = match.value
                val lat = match.groupValues[1].toDoubleOrNull() ?: continue
                val lon = match.groupValues[2].toDoubleOrNull() ?: continue
                val altitude = Regex(""""altitude"\s*:\s*(-?\d+\.?\d*)""")
                    .find(fullObj)?.groupValues?.get(1)?.toDoubleOrNull()
                points.add(GarminPoint(lat = lat, lon = lon, altitude = altitude))
            }

            if (points.isEmpty()) {
                val reversedPattern = Regex("""\{[^}]*"lon"\s*:\s*(-?\d+\.?\d*)[^}]*"lat"\s*:\s*(-?\d+\.?\d*)[^}]*\}""")
                for (match in reversedPattern.findAll(json)) {
                    val fullObj = match.value
                    val lon = match.groupValues[1].toDoubleOrNull() ?: continue
                    val lat = match.groupValues[2].toDoubleOrNull() ?: continue
                    val altitude = Regex(""""altitude"\s*:\s*(-?\d+\.?\d*)""")
                        .find(fullObj)?.groupValues?.get(1)?.toDoubleOrNull()
                    points.add(GarminPoint(lat = lat, lon = lon, altitude = altitude))
                }
            }

            return points.ifEmpty { null }
        }

        fun extractEncodedPolyline(html: String): String? {
            val patterns = listOf(
                Regex(""""encodedPolyline"\s*:\s*"([^"]+)""""),
                Regex(""""encoded_polyline"\s*:\s*"([^"]+)""""),
            )
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) return ImporterUtils.unescapeJsonString(match.groupValues[1])
            }
            return null
        }
    }
}
