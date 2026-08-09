package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import no.synth.where.util.Logger

/** Non-2xx response from the Strava API. [isAuthError]/[isRateLimited] let callers react specifically. */
class StravaApiException(val statusCode: Int) : Exception("Strava API error $statusCode") {
    val isAuthError: Boolean get() = statusCode == 401 || statusCode == 403
    val isRateLimited: Boolean get() = statusCode == 429
}

/**
 * Authenticated client for the Strava REST API. The bearer token is a short-lived access token
 * minted on-device by [StravaTokenManager] from the user's own app credentials (BYO OAuth).
 */
class StravaApiClient(
    private val client: HttpClient = createDefaultHttpClient()
) {
    /** List the athlete's own planned routes (all pages). Includes private routes with read_all. */
    suspend fun listRoutes(accessToken: String, athleteId: Long, perPage: Int = 200): List<StravaRoute> {
        val all = mutableListOf<StravaRoute>()
        var page = 1
        while (true) {
            val url = "$API_BASE/athletes/$athleteId/routes?per_page=$perPage&page=$page"
            val resp = client.get(url) { header(HttpHeaders.Authorization, "Bearer $accessToken") }
            ensureSuccess(resp)
            val batch = parseRoutes(resp.bodyAsText())
            all += batch
            if (batch.size < perPage) break
            page++
        }
        return all
    }

    /** Fetch a route's full geometry as GPX text, ready for [Track.fromGPX]. */
    suspend fun exportRouteGpx(accessToken: String, routeId: Long): String {
        val url = "$API_BASE/routes/$routeId/export_gpx"
        val resp = client.get(url) { header(HttpHeaders.Authorization, "Bearer $accessToken") }
        ensureSuccess(resp)
        return resp.bodyAsText()
    }

    private fun ensureSuccess(resp: HttpResponse) {
        if (!resp.status.isSuccess()) throw StravaApiException(resp.status.value)
    }

    companion object {
        private const val API_BASE = "https://www.strava.com/api/v3"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Parse the JSON array returned by GET /athletes/{id}/routes into route summaries. */
        fun parseRoutes(body: String): List<StravaRoute> {
            return try {
                json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.longOrNull
                        ?: obj["id_str"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?: return@mapNotNull null
                    StravaRoute(
                        id = id,
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Strava route $id",
                        distanceMeters = obj["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        elevationGainMeters = obj["elevation_gain"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        starred = obj["starred"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                }
            } catch (e: Exception) {
                Logger.e(e, "Failed to parse Strava routes response")
                emptyList()
            }
        }
    }
}
