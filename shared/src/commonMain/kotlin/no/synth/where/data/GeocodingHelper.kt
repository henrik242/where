package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger

private const val BASE_URL = "https://nominatim.openstreetmap.org"

object GeocodingHelper {
    var client: HttpClient = createDefaultHttpClient()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content

    private fun JsonObject.broadLocation(): String? =
        string("village") ?: string("town") ?: string("city") ?: string("municipality")

    private fun withBroad(name: String, broad: String?): String =
        if (broad != null && name != broad) "$name, $broad" else name

    private suspend fun fetchJson(url: String): JsonObject? {
        val response = client.get(url)
        if (response.status.value !in 200..299) return null
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private suspend fun searchNearbyPeak(latLng: LatLng): String? {
        val delta = 0.005 // ~500m
        val viewbox = "${latLng.longitude - delta},${latLng.latitude - delta},${latLng.longitude + delta},${latLng.latitude + delta}"
        val response = client.get("$BASE_URL/search?q=%5Bnatural%3Dpeak%5D&format=json&limit=1&viewbox=$viewbox&bounded=1")
        if (response.status.value !in 200..299) return null
        val results = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        if (results.isEmpty()) return null
        return results[0].jsonObject["name"]?.jsonPrimitive?.content
    }

    private suspend fun reverseGeocodePoiName(latLng: LatLng): Pair<String, String?>? {
        val json = fetchJson("$BASE_URL/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1&layer=poi")
            ?: return null
        val name = json.string("name")
        if (name.isNullOrBlank()) return null
        return Pair(name, json["address"]?.jsonObject?.broadLocation())
    }

    suspend fun reverseGeocode(latLng: LatLng): String? = withContext(Dispatchers.Default) {
        try {
            // Try POI layer first to find named features (historic sites, natural features, etc.)
            val poi = try { reverseGeocodePoiName(latLng) } catch (_: Exception) { null }
            if (poi != null) return@withContext withBroad(poi.first, poi.second)

            val json = fetchJson("$BASE_URL/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1")
                ?: return@withContext null
            val address = json["address"]?.jsonObject ?: return@withContext null

            val specific = address.string("road")
                ?: address.string("hamlet")
                ?: address.string("isolated_dwelling")
                ?: address.string("farm")
                ?: address.string("neighbourhood")
                ?: address.string("suburb")
            val locality = address.string("locality")
            val broad = address.broadLocation()

            if (specific != null) return@withContext withBroad(specific, broad)

            // No precise name — check for a nearby peak before using locality
            try {
                val peak = searchNearbyPeak(latLng)
                if (peak != null) return@withContext withBroad(peak, broad)
            } catch (_: Exception) { }

            if (locality != null) return@withContext withBroad(locality, broad)
            if (broad != null) return@withContext broad

            val county = address.string("county")
            if (!county.isNullOrEmpty()) return@withContext county

            json.string("display_name")?.split(",")?.firstOrNull()?.trim()
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
            val json = fetchJson("$BASE_URL/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1&zoom=10")
            val address = json?.get("address")?.jsonObject

            if (address != null) {
                val place = address.string("village")
                    ?: address.string("town")
                    ?: address.string("city")
                    ?: address.string("hamlet")
                    ?: address.string("suburb")
                val municipality = address.string("municipality")
                val county = address.string("county")

                when {
                    place != null && municipality != null && place != municipality -> "$place, $municipality"
                    place != null && county != null -> "$place, $county"
                    municipality != null -> municipality
                    place != null -> place
                    county != null -> county
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            Logger.e(e, "Error reverse geocoding area")
            null
        }
        if (name != null) areaNameCache[key] = name
        name
    }
}
