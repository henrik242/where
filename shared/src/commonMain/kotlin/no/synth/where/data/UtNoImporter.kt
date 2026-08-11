package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import no.synth.where.util.Logger

/**
 * Imports UT.no trips and route descriptions via the public GPX endpoint that the
 * "Last ned GPX" link on ut.no points at: https://ut.no/api/gpx/{trip|route}/{id}.
 */
class UtNoImporter(
    private val client: HttpClient = createDefaultHttpClient()
) {

    suspend fun importFromUrl(
        input: String,
        addElevation: Boolean = true
    ): Track? {
        val ref = parseUtNoUrl(input)
        if (ref == null) {
            Logger.e("Could not parse UT.no URL: $input")
            return null
        }

        val gpx = fetchGpx(ref) ?: return null

        val track = Track.fromGPX(gpx)
        if (track == null || track.points.isEmpty()) {
            Logger.e("No coordinates found in UT.no GPX for ${ref.type.label} ${ref.id}")
            return null
        }
        Logger.d("Extracted ${track.points.size} points from UT.no ${ref.type.label} ${ref.id}")

        val named = if (track.name.isBlank() || track.name == Track.DEFAULT_IMPORT_NAME) {
            track.copy(name = "UT.no ${ref.type.label} ${ref.id}")
        } else {
            track
        }

        if (!addElevation || named.points.any { it.altitude != null }) return named

        val enriched = ImporterUtils.enrichWithElevation(client, named.points.map { it.latLng })
        return named.copy(
            points = named.points.mapIndexed { i, point ->
                point.copy(altitude = enriched.getOrNull(i)?.altitude)
            }
        )
    }

    private suspend fun fetchGpx(ref: UtNoRef): String? {
        val url = gpxUrl(ref)
        return try {
            val response = client.get(url)
            val body = response.bodyAsText()
            when {
                !response.status.isSuccess() -> {
                    Logger.e("UT.no GPX request to $url failed: ${response.status}, ${body.take(200)}")
                    null
                }
                // ut.no answers unknown ids with a JSON error body and, when the CDN is unhappy,
                // with an HTML page - neither parses as GPX, so reject them with a usable message.
                !body.contains("<gpx", ignoreCase = true) -> {
                    Logger.e("UT.no returned no GPX from $url: ${body.take(200)}")
                    null
                }
                else -> body
            }
        } catch (e: Exception) {
            Logger.e(e, "UT.no GPX request to $url failed")
            null
        }
    }

    enum class UtNoType(val label: String) {
        TRIP("trip"),
        ROUTE("route")
    }

    data class UtNoRef(val id: Int, val type: UtNoType)

    companion object {

        private val pathTypes = mapOf(
            "turforslag" to UtNoType.TRIP,
            "kart/tur" to UtNoType.TRIP,
            "kart/turforslag" to UtNoType.TRIP,
            "api/gpx/trip" to UtNoType.TRIP,
            "rutebeskrivelse" to UtNoType.ROUTE,
            "kart/rutebeskrivelse" to UtNoType.ROUTE,
            "api/gpx/route" to UtNoType.ROUTE
        )

        // Longest path first so kart/turforslag wins over kart/tur
        private val urlPattern = Regex(
            """ut\.no/(${pathTypes.keys.sortedByDescending { it.length }.joinToString("|")})/(\d+)""",
            RegexOption.IGNORE_CASE
        )

        fun parseUtNoUrl(input: String): UtNoRef? {
            val match = urlPattern.find(input.trim()) ?: return null
            val type = pathTypes[match.groupValues[1].lowercase()] ?: return null
            val id = match.groupValues[2].toIntOrNull() ?: return null
            return UtNoRef(id = id, type = type)
        }

        fun gpxUrl(ref: UtNoRef): String = "https://ut.no/api/gpx/${ref.type.label}/${ref.id}"
    }
}
