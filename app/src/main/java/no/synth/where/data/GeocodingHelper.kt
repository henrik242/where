package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import timber.log.Timber

object GeocodingHelper {
    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 10_000
        }
    }

    suspend fun reverseGeocode(latLng: LatLng): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1"
            val response = client.get(url) {
                header("User-Agent", "Where-App/1.0")
            }
            if (response.status.value !in 200..299) return@withContext null

            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val address = json["address"]?.jsonObject ?: return@withContext null

            val road = address["road"]?.jsonPrimitive?.content
            if (!road.isNullOrEmpty()) return@withContext road

            val village = address["village"]?.jsonPrimitive?.content
            if (!village.isNullOrEmpty()) return@withContext village

            val town = address["town"]?.jsonPrimitive?.content
            if (!town.isNullOrEmpty()) return@withContext town

            val city = address["city"]?.jsonPrimitive?.content
            if (!city.isNullOrEmpty()) return@withContext city

            val county = address["county"]?.jsonPrimitive?.content
            if (!county.isNullOrEmpty()) return@withContext county

            val displayName = json["display_name"]?.jsonPrimitive?.content
            if (!displayName.isNullOrEmpty()) {
                val parts = displayName.split(",")
                return@withContext parts.firstOrNull()?.trim()
            }

            null
        } catch (e: Exception) {
            Timber.e(e, "Error reverse geocoding")
            null
        }
    }
}
