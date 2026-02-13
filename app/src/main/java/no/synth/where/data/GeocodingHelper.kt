package no.synth.where.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import okhttp3.Request
import org.json.JSONObject
import no.synth.where.data.geo.LatLng
import java.util.concurrent.TimeUnit

object GeocodingHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun reverseGeocode(latLng: LatLng): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=${latLng.latitude}&lon=${latLng.longitude}&format=json&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Where-App/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body.string())
            val address = json.optJSONObject("address") ?: return@withContext null

            // Try to build a nice name from available data
            val road = address.optString("road")
            if (road.isNotEmpty()) return@withContext road

            val village = address.optString("village")
            if (village.isNotEmpty()) return@withContext village

            val town = address.optString("town")
            if (town.isNotEmpty()) return@withContext town

            val city = address.optString("city")
            if (city.isNotEmpty()) return@withContext city

            val county = address.optString("county")
            if (county.isNotEmpty()) return@withContext county

            val displayName = json.optString("display_name")
            if (displayName.isNotEmpty()) {
                // Extract first meaningful part
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

