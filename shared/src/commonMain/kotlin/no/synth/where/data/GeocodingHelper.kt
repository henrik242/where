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
import kotlin.concurrent.Volatile

private const val NOMINATIM = "https://nominatim.openstreetmap.org"
private const val MAX_CACHE_ENTRIES = 1000

// Public Overpass instances rate-limit and time out independently of each other, so a query
// that one rejects usually succeeds on the next.
private val OVERPASS_MIRRORS = listOf(
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter"
)

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

    /** Mirror that answered last, so a struggling one is not picked first on every lookup. */
    @Volatile
    private var preferredMirror = 0

    private suspend fun overpassQuery(query: String): List<JsonObject> {
        val start = preferredMirror
        for (offset in OVERPASS_MIRRORS.indices) {
            val index = (start + offset) % OVERPASS_MIRRORS.size
            val result = overpassAttempt(OVERPASS_MIRRORS[index], query)
            if (result.success) {
                preferredMirror = index
                return result.elements
            }
            // A rejected query fails the same way everywhere; only load problems are worth a mirror hop.
            if (!result.retryable) return emptyList()
        }
        return emptyList()
    }

    private data class OverpassResult(val success: Boolean, val retryable: Boolean, val elements: List<JsonObject>)

    private suspend fun overpassAttempt(endpoint: String, query: String): OverpassResult {
        val response = try {
            client.submitForm(endpoint, parameters { append("data", query) })
        } catch (e: Exception) {
            Logger.d("overpass call to $endpoint failed: ${e.message}")
            return OverpassResult(success = false, retryable = true, elements = emptyList())
        }
        val status = response.status.value
        if (status in 200..299) {
            val elements = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["elements"]?.jsonArray
                ?.map { it.jsonObject }
                ?: emptyList()
            return OverpassResult(success = true, retryable = false, elements = elements)
        }
        Logger.d("overpass $endpoint status=$status")
        val retryable = status == 429 || status in 500..599
        return OverpassResult(success = false, retryable = retryable, elements = emptyList())
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

    private suspend fun searchEnclosingWater(latLng: LatLng): String? {
        // Plain is_in carries the source tags on the areas it returns, so filtering here is
        // cheaper for the server than resolving each area back to its way/relation with pivot.
        val elements = overpassQuery(
            "[out:json][timeout:10];is_in(${latLng.latitude},${latLng.longitude});out tags;"
        )
        return elements.firstNotNullOfOrNull { element ->
            val tags = element["tags"]?.jsonObject ?: return@firstNotNullOfOrNull null
            tags.string("name")?.takeIf { tags.string("natural") == "water" }
        }
    }

    private val landmarkNaturalTypes = setOf("water", "peak", "bay", "spring", "beach", "glacier", "cliff")
    private val landmarkLeisureTypes = setOf("park", "nature_reserve", "stadium")

    // POI classes that normally sit inside a building (a cafe, an office, a defibrillator on a
    // wall). The building is the more useful answer than the POI that happened to be nearest.
    private val inBuildingClasses = setOf("amenity", "shop", "office", "emergency", "healthcare", "craft")

    private fun isLandmark(cls: String?, type: String?): Boolean = when (cls) {
        "historic", "tourism", "waterway" -> true
        "natural" -> type in landmarkNaturalTypes
        "leisure" -> type in landmarkLeisureTypes
        // Named guideposts identify the place they stand at (common in hiking areas).
        "information" -> type == "guidepost"
        else -> false
    }

    private suspend fun findLandmark(latLng: LatLng): Pair<String, String?>? {
        val json = fetchJson("$NOMINATIM/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1&layer=poi,natural")
            ?: return null
        val name = json.string("name")
        val cls = json.string("class")
        val type = json.string("type")
        val broad = json["address"]?.jsonObject?.broadLocation()
        if (!name.isNullOrBlank() && isLandmark(cls, type)) return name to broad
        // Non-landmark POIs (bars, parking lots, shops) may be inside a named building
        if (cls in inBuildingClasses) {
            val building = try { searchEnclosingBuilding(latLng) } catch (_: Exception) { null }
            if (!building.isNullOrBlank()) return building to broad
        }
        // Nominatim points on/near water often resolve to small natural features
        // (cape, island, strait). Look up the enclosing named water body instead.
        if (cls == "natural") {
            val water = try { searchEnclosingWater(latLng) } catch (_: Exception) { null }
            if (water != null) return water to broad
        }
        val peak = try { searchNearbyPeak(latLng) } catch (_: Exception) { null }
        if (peak != null) return peak to broad
        return null
    }

    suspend fun reverseGeocode(latLng: LatLng): String? = withContext(Dispatchers.Default) {
        val key = cacheKey(latLng)
        cacheMutex.withLock { nameCache[key] }?.let { return@withContext it }
        val name = try {
            val landmarkDef = async { try { findLandmark(latLng) } catch (_: Exception) { null } }
            val addressDef = async {
                try { fetchJson("$NOMINATIM/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1") }
                catch (_: Exception) { null }
            }

            val landmark = landmarkDef.await()
            if (landmark != null) {
                val broad = landmark.second
                    ?: addressDef.await()?.get("address")?.jsonObject?.broadLocation()
                withBroad(landmark.first, broad)
            } else {
                resolveAddressName(addressDef.await())
            }
        } catch (e: Exception) {
            Logger.e(e, "Error reverse geocoding")
            null
        }
        if (name != null) cacheMutex.withLock { nameCache[key] = name }
        name
    }

    private fun resolveAddressName(json: JsonObject?): String? {
        if (json == null) return null
        val address = json["address"]?.jsonObject ?: return null
        val broad = address.broadLocation()
        val specific = address.string("road")
            ?: address.string("hamlet")
            ?: address.string("isolated_dwelling")
            ?: address.string("farm")
            ?: address.string("neighbourhood")
            ?: address.string("suburb")
        if (specific != null) return withBroad(specific, broad)

        val locality = address.string("locality")
        if (locality != null) return withBroad(locality, broad)
        if (broad != null) return broad

        val county = address.string("county")
        if (!county.isNullOrEmpty()) return county

        return json.string("display_name")?.split(",")?.firstOrNull()?.trim()
    }

    private val cacheMutex = Mutex()
    private val nameCache = LruCache(MAX_CACHE_ENTRIES)
    private val areaNameCache = LruCache(MAX_CACHE_ENTRIES)

    private fun cacheKey(latLng: LatLng): String =
        "${latLng.latitude.toBits()}_${latLng.longitude.toBits()}"

    suspend fun clearCaches() {
        cacheMutex.withLock {
            nameCache.clear()
            areaNameCache.clear()
        }
    }

    private class LruCache(private val maxSize: Int) {
        private val entries = LinkedHashMap<String, String>()

        operator fun get(key: String): String? {
            val value = entries.remove(key) ?: return null
            entries[key] = value
            return value
        }

        operator fun set(key: String, value: String) {
            entries.remove(key)
            entries[key] = value
            if (entries.size > maxSize) entries.remove(entries.keys.iterator().next())
        }

        fun clear() = entries.clear()
    }

    suspend fun reverseGeocodeArea(latLng: LatLng): String? = withContext(Dispatchers.Default) {
        val key = cacheKey(latLng)
        cacheMutex.withLock { areaNameCache[key] }?.let { return@withContext it }
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
        if (name != null) cacheMutex.withLock { areaNameCache[key] = name }
        name
    }
}
