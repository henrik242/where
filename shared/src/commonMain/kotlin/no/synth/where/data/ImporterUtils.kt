package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

/** Shared utilities for URL-based track importers. */
object ImporterUtils {

    private const val ELEVATION_BATCH_SIZE = 100

    // --- Elevation enrichment via Open-Meteo ---

    suspend fun enrichWithElevation(client: HttpClient, points: List<LatLng>): List<TrackPoint> {
        val now = currentTimeMillis()
        return try {
            val elevations = fetchElevations(client, points)
            points.mapIndexed { i, latLng ->
                TrackPoint(
                    latLng = latLng,
                    timestamp = now,
                    altitude = elevations.getOrNull(i)
                )
            }
        } catch (e: Exception) {
            Logger.e(e, "Elevation enrichment failed, using points without elevation")
            points.map { TrackPoint(latLng = it, timestamp = now) }
        }
    }

    private suspend fun fetchElevations(client: HttpClient, points: List<LatLng>): List<Double> {
        val result = mutableListOf<Double>()
        for (batch in points.chunked(ELEVATION_BATCH_SIZE)) {
            val lats = batch.joinToString(",") { it.latitude.toString() }
            val lngs = batch.joinToString(",") { it.longitude.toString() }
            val url = "https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lngs"
            val responseText = client.get(url).bodyAsText()
            val json = Json.parseToJsonElement(responseText).jsonObject
            val elevations = json["elevation"]?.jsonArray ?: JsonArray(emptyList())
            for (ele in elevations) {
                result.add(ele.jsonPrimitive.double)
            }
        }
        return result
    }

    // --- HTML metadata extraction ---

    /** Extracts og:title content, handling both attribute orderings. */
    fun extractOgTitle(html: String): String? {
        return Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""")
            .find(html)?.groupValues?.get(1)
            ?: Regex("""<meta\s+content="([^"]+)"\s+property="og:title"""")
                .find(html)?.groupValues?.get(1)
    }

    /** Extracts text content of the <title> tag. */
    fun extractTitleTag(html: String): String? {
        return Regex("""<title>([^<]+)</title>""")
            .find(html)?.groupValues?.get(1)?.trim()
    }

    /**
     * Extracts the first JSON "name" value from HTML that looks like a human-readable name.
     * Filters out URLs, encoded polylines, and hash-like strings.
     */
    fun extractJsonName(html: String): String? {
        val value = Regex(""""name"\s*:\s*"([^"]{1,200})"""")
            .find(html)?.groupValues?.get(1) ?: return null
        return if (looksLikeName(value)) unescapeJsonString(value) else null
    }

    /** Heuristic: is this value likely a human-readable name vs a URL/hash/polyline? */
    fun looksLikeName(value: String): Boolean {
        if (value.length > 150) return false
        if (value.startsWith("http")) return false
        if (value.all { it.isLetterOrDigit() || it == '_' || it == '\\' }) return false
        return true
    }

    /**
     * Strips a service-name suffix from a page title.
     * Handles patterns like "Tour Name on Service", "Tour Name | Service", "Tour Name - Service".
     */
    fun cleanTitleSuffix(raw: String, vararg serviceNames: String): String? {
        var cleaned = raw
        for (name in serviceNames) {
            cleaned = cleaned
                .replace(Regex("""\s+on\s+$name$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*\|.*$name.*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*\|.*$"""), "")
                .replace(Regex("""\s*[–—-]\s*$name.*$""", RegexOption.IGNORE_CASE), "")
        }
        return cleaned.trim().ifBlank { null }
    }

    // --- JSON string unescaping ---

    fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\\/", "/")
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\u0026", "&")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .replace("\\u00e4", "ä")
            .replace("\\u00f6", "ö")
            .replace("\\u00fc", "ü")
            .replace("\\u00df", "ß")
            .replace("\\u00e5", "å")
            .replace("\\u00f8", "ø")
            .replace("\\u00e6", "æ")
    }
}
