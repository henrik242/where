package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

data class TerrainInfo(
    val elevation: Double,
    val slopeDegrees: Double
)

object TerrainClient {
    var client: HttpClient = createDefaultHttpClient()

    private const val OFFSET_METERS = 10.0
    private const val LAT_OFFSET = OFFSET_METERS / 111320.0
    private fun lonOffset(lat: Double) = OFFSET_METERS / (111320.0 * cos(lat * PI / 180.0))

    suspend fun getTerrainInfo(latLng: LatLng): TerrainInfo? = withContext(Dispatchers.Default) {
        try {
            val lat = latLng.latitude
            val lon = latLng.longitude
            val dLat = LAT_OFFSET
            val dLon = lonOffset(lat)

            val points = listOf(
                listOf(lon, lat),           // center
                listOf(lon, lat + dLat),    // north
                listOf(lon, lat - dLat),    // south
                listOf(lon + dLon, lat),    // east
                listOf(lon - dLon, lat)     // west
            )
            val punkter = points.joinToString(",", prefix = "[", postfix = "]") { "[${it[0]},${it[1]}]" }
            val url = "https://ws.geonorge.no/hoydedata/v1/punkt?punkter=$punkter&koordsys=4258"

            val response = client.get(url)
            if (response.status.value !in 200..299) return@withContext null

            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val results = json["punkter"]?.jsonArray ?: return@withContext null
            if (results.size < 5) return@withContext null

            val elevations = results.map { element ->
                element.jsonObject["z"]?.jsonPrimitive?.content?.toDoubleOrNull()
            }

            val centerElev = elevations[0] ?: return@withContext null
            val northElev = elevations[1] ?: return@withContext null
            val southElev = elevations[2] ?: return@withContext null
            val eastElev = elevations[3] ?: return@withContext null
            val westElev = elevations[4] ?: return@withContext null

            val dzDy = (northElev - southElev) / (2 * OFFSET_METERS)
            val dzDx = (eastElev - westElev) / (2 * OFFSET_METERS)
            val slopeRadians = atan2(sqrt(dzDx * dzDx + dzDy * dzDy), 1.0)
            val slopeDegrees = slopeRadians * 180.0 / kotlin.math.PI

            TerrainInfo(
                elevation = centerElev,
                slopeDegrees = slopeDegrees
            )
        } catch (e: Exception) {
            Logger.e(e, "Error fetching terrain info")
            null
        }
    }
}
