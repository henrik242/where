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

object GeocodingHelper {
    var client: HttpClient = createDefaultHttpClient()

    private suspend fun searchNearbyPeak(latLng: LatLng): String? {
        val delta = 0.005 // ~500m
        val viewbox = "${latLng.longitude - delta},${latLng.latitude - delta},${latLng.longitude + delta},${latLng.latitude + delta}"
        val url = "https://nominatim.openstreetmap.org/search?q=%5Bnatural%3Dpeak%5D&format=json&limit=1&viewbox=$viewbox&bounded=1"
        val response = client.get(url) {

        }
        if (response.status.value !in 200..299) return null
        val results = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        if (results.isEmpty()) return null
        return results[0].jsonObject["name"]?.jsonPrimitive?.content
    }

    suspend fun reverseGeocode(latLng: LatLng): String? = withContext(Dispatchers.Default) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1"
            val response = client.get(url) {
    
            }
            if (response.status.value !in 200..299) return@withContext null

            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val address = json["address"]?.jsonObject ?: return@withContext null

            val specific = address["road"]?.jsonPrimitive?.content
                ?: address["hamlet"]?.jsonPrimitive?.content
                ?: address["isolated_dwelling"]?.jsonPrimitive?.content
                ?: address["farm"]?.jsonPrimitive?.content
                ?: address["neighbourhood"]?.jsonPrimitive?.content
                ?: address["suburb"]?.jsonPrimitive?.content

            val locality = address["locality"]?.jsonPrimitive?.content

            val broad = address["village"]?.jsonPrimitive?.content
                ?: address["town"]?.jsonPrimitive?.content
                ?: address["city"]?.jsonPrimitive?.content
                ?: address["municipality"]?.jsonPrimitive?.content

            if (specific != null && broad != null && specific != broad) return@withContext "$specific, $broad"
            if (specific != null) return@withContext specific

            // No precise name — check for a nearby peak before using locality
            try {
                val peak = searchNearbyPeak(latLng)
                if (peak != null && broad != null) return@withContext "$peak, $broad"
                if (peak != null) return@withContext peak
            } catch (_: Exception) { }

            if (locality != null && broad != null && locality != broad) return@withContext "$locality, $broad"
            if (locality != null) return@withContext locality
            if (broad != null) return@withContext broad

            val county = address["county"]?.jsonPrimitive?.content
            if (!county.isNullOrEmpty()) return@withContext county

            val displayName = json["display_name"]?.jsonPrimitive?.content
            if (!displayName.isNullOrEmpty()) {
                val parts = displayName.split(",")
                return@withContext parts.firstOrNull()?.trim()
            }

            null
        } catch (e: Exception) {
            Logger.e(e, "Error reverse geocoding")
            null
        }
    }

    private val areaNameCache = mutableMapOf<String, String>()

    private fun areaCacheKey(latLng: LatLng): String =
        "${latLng.latitude.toBits()}_${latLng.longitude.toBits()}"

    suspend fun reverseGeocodeArea(latLng: LatLng): String? = withContext(Dispatchers.Default) {
        val key = areaCacheKey(latLng)
        areaNameCache[key]?.let { return@withContext it }
        val name = try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1&zoom=10"
            val response = client.get(url) {
    
            }
            if (response.status.value !in 200..299) {
                null
            } else {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val address = json["address"]?.jsonObject

                if (address != null) {
                    val place = address["village"]?.jsonPrimitive?.content
                        ?: address["town"]?.jsonPrimitive?.content
                        ?: address["city"]?.jsonPrimitive?.content
                        ?: address["hamlet"]?.jsonPrimitive?.content
                        ?: address["suburb"]?.jsonPrimitive?.content
                    val municipality = address["municipality"]?.jsonPrimitive?.content
                    val county = address["county"]?.jsonPrimitive?.content

                    when {
                        place != null && municipality != null && place != municipality -> "$place, $municipality"
                        place != null && county != null -> "$place, $county"
                        municipality != null -> municipality
                        place != null -> place
                        county != null -> county
                        else -> null
                    }
                } else null
            }
        } catch (e: Exception) {
            Logger.e(e, "Error reverse geocoding area")
            null
        }
        if (name != null) areaNameCache[key] = name
        name
    }
}
