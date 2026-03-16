package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger

private const val NOMINATIM = "https://nominatim.openstreetmap.org"
private const val OVERPASS = "https://overpass-api.de/api/interpreter"

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

    private suspend fun overpassQuery(query: String): List<JsonObject> {
        val response = client.submitForm(OVERPASS, parameters { append("data", query) })
        if (response.status.value !in 200..299) return emptyList()
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["elements"]?.jsonArray
            ?.map { it.jsonObject }
            ?: emptyList()
    }

    private suspend fun searchNearbyPeak(latLng: LatLng): String? {
        val delta = 0.005 // ~500m
        val viewbox = "${latLng.longitude - delta},${latLng.latitude + delta},${latLng.longitude + delta},${latLng.latitude - delta}"
        val response = client.get("$NOMINATIM/search?q=%5Bnatural%3Dpeak%5D&format=json&limit=1&viewbox=$viewbox&bounded=1")
        if (response.status.value !in 200..299) return null
        val results = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        if (results.isEmpty()) return null
        return results[0].jsonObject["name"]?.jsonPrimitive?.content
    }

    private suspend fun searchEnclosingBuilding(latLng: LatLng): String? {
        val elements = overpassQuery(
            "[out:json][timeout:10];(" +
                "way(around:50,${latLng.latitude},${latLng.longitude})[\"name\"][\"building\"];" +
                "relation(around:50,${latLng.latitude},${latLng.longitude})[\"name\"][\"building\"];" +
                ");out tags 1;"
        )
        if (elements.isEmpty()) return null
        return elements[0]["tags"]?.jsonObject?.string("name")
    }

    private val landmarkNaturalTypes = setOf("water", "peak", "bay", "spring", "beach", "glacier", "cliff")
    private val landmarkLeisureTypes = setOf("park", "nature_reserve", "stadium")

    private fun isLandmark(cls: String?, type: String?): Boolean = when (cls) {
        "historic", "tourism", "waterway" -> true
        "natural" -> type in landmarkNaturalTypes
        "leisure" -> type in landmarkLeisureTypes
        else -> false
    }

    private suspend fun findLandmark(latLng: LatLng): Pair<String, String?>? {
        val json = fetchJson("$NOMINATIM/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1&layer=poi,natural")
            ?: return null
        val name = json.string("name")
        val broad = json["address"]?.jsonObject?.broadLocation()
        if (!name.isNullOrBlank() && isLandmark(json.string("class"), json.string("type")))
            return name to broad
        // Non-landmark POIs (bars, parking lots, shops) may be inside a named building
        val cls = json.string("class")
        if (cls in setOf("amenity", "shop", "office")) {
            val building = try { searchEnclosingBuilding(latLng) } catch (_: Exception) { null }
            if (!building.isNullOrBlank()) return building to broad
        }
        val peak = try { searchNearbyPeak(latLng) } catch (_: Exception) { null }
        if (peak != null) return peak to broad
        return null
    }

    suspend fun reverseGeocode(latLng: LatLng): String? = withContext(Dispatchers.Default) {
        try {
            val landmarkDef = async { try { findLandmark(latLng) } catch (_: Exception) { null } }
            val addressDef = async {
                try { fetchJson("$NOMINATIM/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1") }
                catch (_: Exception) { null }
            }

            val landmark = landmarkDef.await()
            if (landmark != null) {
                val broad = landmark.second
                    ?: addressDef.await()?.get("address")?.jsonObject?.broadLocation()
                return@withContext withBroad(landmark.first, broad)
            }

            val json = addressDef.await() ?: return@withContext null
            val address = json["address"]?.jsonObject ?: return@withContext null
            val broad = address.broadLocation()

            val specific = address.string("road")
                ?: address.string("hamlet")
                ?: address.string("isolated_dwelling")
                ?: address.string("farm")
                ?: address.string("neighbourhood")
                ?: address.string("suburb")
            if (specific != null) return@withContext withBroad(specific, broad)

            val locality = address.string("locality")
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

    private val areaCacheMutex = Mutex()
    private val areaNameCache = mutableMapOf<String, String>()

    private fun areaCacheKey(latLng: LatLng): String =
        "${latLng.latitude.toBits()}_${latLng.longitude.toBits()}"

    suspend fun reverseGeocodeArea(latLng: LatLng): String? = withContext(Dispatchers.Default) {
        val key = areaCacheKey(latLng)
        areaCacheMutex.withLock { areaNameCache[key] }?.let { return@withContext it }
        val name = try {
            val json = fetchJson("$NOMINATIM/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1&zoom=10")
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
        if (name != null) areaCacheMutex.withLock { areaNameCache[key] = name }
        name
    }
}
