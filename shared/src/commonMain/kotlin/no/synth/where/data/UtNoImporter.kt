package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

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

        val (name, points) = fetchRoute(ref) ?: run {
            Logger.e("Could not fetch data from UT.no for ${ref.type} ${ref.id}")
            return null
        }

        if (points.isEmpty()) {
            Logger.e("No coordinates found for UT.no ${ref.type} ${ref.id}")
            return null
        }
        Logger.d("Extracted ${points.size} points from UT.no ${ref.type} ${ref.id}")

        val hasElevation = points.any { it.third != null }
        val now = currentTimeMillis()
        val trackPoints = if (hasElevation) {
            points.map { (lat, lng, alt) ->
                TrackPoint(latLng = LatLng(lat, lng), timestamp = now, altitude = alt)
            }
        } else if (addElevation) {
            ImporterUtils.enrichWithElevation(client, points.map { (lat, lng, _) -> LatLng(lat, lng) })
        } else {
            points.map { (lat, lng, _) ->
                TrackPoint(latLng = LatLng(lat, lng), timestamp = now)
            }
        }

        val fallbackName = "UT.no ${ref.type.label} ${ref.id}"
        return Track(
            name = name ?: fallbackName,
            points = trackPoints,
            startTime = now,
            endTime = now,
            isRecording = false
        )
    }

    private data class RouteData(
        val name: String?,
        val points: List<Triple<Double, Double, Double?>>
    )

    private suspend fun fetchRoute(ref: UtNoRef): RouteData? {
        return try {
            val queryField = when (ref.type) {
                UtNoType.TRIP -> "trip"
                UtNoType.ROUTE -> "route"
            }
            val query = """{ $queryField(id: ${ref.id}) { name geojson } }"""
            val body = """{"query":"${query.replace("\"", "\\\"")}"}"""

            val response = client.post(GRAPHQL_ENDPOINT) {
                contentType(ContentType.Application.Json)
                header("x-ut-api-key", API_KEY)
                header("x-ut-client-name", "ut-web")
                header("Origin", "https://ut.no")
                setBody(body)
            }.bodyAsText()

            val json = lenientJson.parseToJsonElement(response).jsonObject
            val data = json["data"]?.jsonObject?.get(queryField)?.jsonObject ?: return null
            val name = data["name"]?.jsonPrimitive?.content
            val geojson = data["geojson"]?.jsonObject ?: return null
            val points = parseGeoJsonCoordinates(geojson)
            RouteData(name = name, points = points)
        } catch (e: Exception) {
            Logger.d("UT.no API request failed: ${e.message}")
            null
        }
    }

    enum class UtNoType(val label: String) {
        TRIP("trip"),
        ROUTE("route")
    }

    data class UtNoRef(val id: Int, val type: UtNoType)

    companion object {

        private const val GRAPHQL_ENDPOINT =
            "https://ut-backend-api-2-41145913385.europe-north1.run.app/internal/graphql"
        private const val API_KEY = "y9CxVnBzVIj5DRQR8ldbBAMQ1cIxj5Qv"

        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        private val urlPattern = Regex(
            """ut\.no/(?:turforslag|kart/tur|rutebeskrivelse|kart/rutebeskrivelse)/(\d+)"""
        )

        private val typePattern = Regex(
            """ut\.no/(turforslag|kart/tur|rutebeskrivelse|kart/rutebeskrivelse)/"""
        )

        fun parseUtNoUrl(input: String): UtNoRef? {
            val trimmed = input.trim()
            val idMatch = urlPattern.find(trimmed) ?: return null
            val id = idMatch.groupValues[1].toIntOrNull() ?: return null
            val typeMatch = typePattern.find(trimmed) ?: return null
            val type = when (typeMatch.groupValues[1]) {
                "turforslag", "kart/tur" -> UtNoType.TRIP
                "rutebeskrivelse", "kart/rutebeskrivelse" -> UtNoType.ROUTE
                else -> return null
            }
            return UtNoRef(id = id, type = type)
        }

        fun parseGeoJsonCoordinates(
            geojson: kotlinx.serialization.json.JsonObject
        ): List<Triple<Double, Double, Double?>> {
            val coordinates = geojson["coordinates"]?.jsonArray ?: return emptyList()
            return coordinates.mapNotNull { coord ->
                val arr = coord.jsonArray
                if (arr.size < 2) return@mapNotNull null
                val lon = arr[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val lat = arr[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val ele = if (arr.size >= 3) arr[2].jsonPrimitive.doubleOrNull else null
                Triple(lat, lon, ele)
            }
        }
    }
}
