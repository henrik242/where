package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

class StravaImporter(
    private val client: HttpClient = createDefaultHttpClient()
) {

    suspend fun importFromUrl(
        input: String,
        addElevation: Boolean = true
    ): Track? {
        val trimmed = input.trim()
        if (trimmed.contains("strava.app.link", ignoreCase = true)) {
            return importFromAppLink(trimmed, addElevation)
        }

        val ref = parseStravaUrl(trimmed)
        if (ref == null) {
            Logger.e("Could not parse Strava URL: $input")
            return null
        }

        val html = fetchHtml(ref) ?: return null
        val label = ref.type.replaceFirstChar { it.uppercase() }
        return buildTrack(html, fallbackName = "Strava $label ${ref.id}", addElevation, logLabel = "${ref.type} ${ref.id}")
    }

    private suspend fun importFromAppLink(url: String, addElevation: Boolean): Track? {
        val html = try {
            client.get(url).bodyAsText()
        } catch (e: Exception) {
            Logger.e(e, "Failed to fetch Strava app.link page")
            return null
        }

        val canonicalRef = extractCanonicalUrl(html)?.let { parseStravaUrl(it) }
        val fallbackName = canonicalRef?.let {
            "Strava ${it.type.replaceFirstChar { c -> c.uppercase() }} ${it.id}"
        } ?: "Strava Activity"

        return buildTrack(html, fallbackName, addElevation, logLabel = "strava.app.link")
    }

    private suspend fun buildTrack(html: String, fallbackName: String, addElevation: Boolean, logLabel: String): Track? {
        val name = extractName(html)

        // Try __NEXT_DATA__ first (current Strava Next.js format — full coordinates with elevation)
        val nextData = extractFromNextData(html)
        if (nextData != null) {
            if (nextData.points.isEmpty()) {
                Logger.e("No coordinates found in __NEXT_DATA__ for $logLabel")
                return null
            }
            Logger.d("Extracted ${nextData.points.size} points from __NEXT_DATA__ for $logLabel")
            val now = currentTimeMillis()
            val hasElevation = nextData.elevations.any { it != null }
            val trackPoints = when {
                hasElevation -> nextData.points.mapIndexed { i, latLng ->
                    TrackPoint(latLng = latLng, timestamp = now, altitude = nextData.elevations.getOrNull(i))
                }
                addElevation -> ImporterUtils.enrichWithElevation(client, nextData.points)
                else -> nextData.points.map { TrackPoint(latLng = it, timestamp = now) }
            }
            return Track(
                name = name ?: nextData.name ?: fallbackName,
                points = trackPoints,
                startTime = now,
                endTime = now,
                isRecording = false
            )
        }

        // Fall back to encoded polyline extraction (older Strava page format)
        val polyline = extractPolyline(html) ?: run {
            Logger.e("Could not extract data from Strava $logLabel")
            return null
        }
        val points = decodePolyline(polyline)
        if (points.isEmpty()) {
            Logger.e("Decoded polyline was empty for Strava $logLabel")
            return null
        }
        Logger.d("Decoded ${points.size} points from Strava $logLabel (polyline fallback)")
        val now = currentTimeMillis()
        val trackPoints = if (addElevation) {
            ImporterUtils.enrichWithElevation(client, points)
        } else {
            points.map { TrackPoint(latLng = it, timestamp = now) }
        }
        return Track(
            name = name ?: fallbackName,
            points = trackPoints,
            startTime = now,
            endTime = now,
            isRecording = false
        )
    }

    private suspend fun fetchHtml(ref: StravaRef): String? {
        // "activity" → "activities", "route" → "routes"
        val pathSegment = if (ref.type == "activity") "activities" else "${ref.type}s"
        val url = "https://www.strava.com/$pathSegment/${ref.id}"
        return try {
            client.get(url).bodyAsText()
        } catch (e: Exception) {
            Logger.e(e, "Failed to fetch Strava ${ref.type} page")
            null
        }
    }

    data class StravaRef(val type: String, val id: String)

    data class NextDataResult(
        val points: List<LatLng>,
        val elevations: List<Double?>,
        val name: String?
    )

    companion object {
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseStravaUrl(input: String): StravaRef? {
            val trimmed = input.trim()

            val urlPattern = Regex("""strava\.com/(activities|routes)/(\d+)""")
            urlPattern.find(trimmed)?.let { match ->
                val type = if (match.groupValues[1] == "activities") "activity" else "route"
                return StravaRef(type = type, id = match.groupValues[2])
            }

            if (trimmed.all { it.isDigit() } && trimmed.length >= 8) {
                return StravaRef(type = "activity", id = trimmed)
            }

            Regex("""(\d{8,})""").find(trimmed)?.let { match ->
                return StravaRef(type = "activity", id = match.groupValues[1])
            }

            return null
        }

        fun extractFromNextData(html: String): NextDataResult? {
            val startMarker = """<script id="__NEXT_DATA__" type="application/json">"""
            val startIdx = html.indexOf(startMarker)
            if (startIdx == -1) {
                Logger.d("No __NEXT_DATA__ script found (html size=${html.length}, preview=${html.take(200)})")
                return null
            }
            val contentStart = startIdx + startMarker.length
            val endIdx = html.indexOf("</script>", contentStart)
            if (endIdx == -1) return null
            val scriptContent = html.substring(contentStart, endIdx)
            return try {
                val root = lenientJson.parseToJsonElement(scriptContent).jsonObject
                val activity = root["props"]?.jsonObject
                    ?.get("pageProps")?.jsonObject
                    ?.get("activity")?.jsonObject ?: return null

                val name = activity["name"]?.jsonPrimitive?.content
                val streams = activity["streams"]?.jsonObject ?: return null
                val locationArray = streams["location"]?.jsonArray ?: return null

                if (locationArray.isEmpty()) return null

                val points = locationArray.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val lng = obj["lng"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    LatLng(lat, lng)
                }
                if (points.isEmpty()) return null

                val elevationArray = streams["elevation"]?.jsonArray
                val elevations = elevationArray?.map { it.jsonPrimitive.doubleOrNull }
                    ?: points.map { null }

                NextDataResult(points = points, elevations = elevations, name = name)
            } catch (e: Exception) {
                Logger.d("Failed to extract from __NEXT_DATA__: ${e.message}")
                null
            }
        }

        fun extractCanonicalUrl(html: String): String? {
            return Regex("""<link\s+rel="canonical"\s+href="([^"]+)"""")
                .find(html)?.groupValues?.get(1)
                ?: Regex("""<meta\s+property="og:url"\s+content="([^"]+)"""")
                    .find(html)?.groupValues?.get(1)
        }

        fun extractPolyline(html: String): String? {
            val patterns = listOf(
                Regex(""""polyline"\s*:\s*"([^"]+)""""),
                Regex(""""summary_polyline"\s*:\s*"([^"]+)""""),
                Regex("""summaryPolyline"\s*:\s*"([^"]+)""""),
                Regex("""data-polyline="([^"]+)""""),
                Regex("""data-summary-polyline="([^"]+)""""),
            )
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) return ImporterUtils.unescapeJsonString(match.groupValues[1])
            }
            return null
        }

        fun extractName(html: String): String? {
            val ogTitle = ImporterUtils.extractOgTitle(html)
            if (ogTitle != null) return ImporterUtils.cleanTitleSuffix(ogTitle, "Strava")

            val jsonName = ImporterUtils.extractJsonName(html)
            if (jsonName != null) return jsonName

            val title = ImporterUtils.extractTitleTag(html)
            if (title != null) return ImporterUtils.cleanTitleSuffix(title, "Strava")

            return null
        }

        fun decodePolyline(encoded: String): List<LatLng> {
            val points = mutableListOf<LatLng>()
            var index = 0
            var lat = 0
            var lng = 0

            while (index < encoded.length) {
                var shift = 0
                var result = 0
                do {
                    val b = encoded[index++].code - 63
                    result = result or ((b and 0x1F) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

                shift = 0
                result = 0
                do {
                    val b = encoded[index++].code - 63
                    result = result or ((b and 0x1F) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

                points.add(LatLng(lat / 1e5, lng / 1e5))
            }
            return points
        }
    }
}
