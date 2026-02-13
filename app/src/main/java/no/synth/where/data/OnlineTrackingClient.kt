package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.synth.where.BuildConfig
import no.synth.where.util.HmacUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import no.synth.where.data.geo.LatLng
import timber.log.Timber
import java.util.concurrent.TimeUnit

class OnlineTrackingClient(
    private val serverUrl: String,
    private val clientId: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentTrackId: String? = null
    private val hmacSecret = BuildConfig.TRACKING_HMAC_SECRET

    /**
     * Create a signed request with HMAC signature
     */
    private fun createSignedRequest(url: String, jsonBody: String, method: String = "POST"): Request {
        val signature = HmacUtils.generateSignature(jsonBody, hmacSecret)

        val body = jsonBody.toRequestBody("application/json".toMediaType())

        return Request.Builder()
            .url(url)
            .addHeader("X-Client-Id", clientId)
            .addHeader("X-Signature", signature)
            .method(method, if (method == "POST" || method == "PUT") body else null)
            .build()
    }

    fun startTrack(trackName: String) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("userId", clientId)
                    put("name", trackName)
                }

                val request = createSignedRequest("$serverUrl/api/tracks", json.toString())

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    val trackId = JSONObject(responseBody).getString("id")
                    currentTrackId = trackId
                    Timber.d("Track started: %s", trackId)
                } else {
                    Timber.e("Failed to start track: %d", response.code)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting track")
            }
        }
    }

    fun syncExistingTrack(track: Track) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("userId", clientId)
                    put("name", track.name)
                    if (track.points.isNotEmpty()) {
                        val pointsArray = org.json.JSONArray()
                        val firstPoint = JSONObject().apply {
                            put("lat", track.points[0].latLng.latitude)
                            put("lon", track.points[0].latLng.longitude)
                            put("timestamp", track.points[0].timestamp)
                            track.points[0].altitude?.let { put("altitude", it) }
                            track.points[0].accuracy?.let { put("accuracy", it.toDouble()) }
                        }
                        pointsArray.put(firstPoint)
                        put("points", pointsArray)
                    }
                }

                val request = createSignedRequest("$serverUrl/api/tracks", json.toString())

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    val trackId = JSONObject(responseBody).getString("id")
                    currentTrackId = trackId
                    Timber.d("Track synced: %s", trackId)

                    track.points.drop(1).forEach { point ->
                        sendPointSync(trackId, point.latLng, point.altitude, point.accuracy)
                    }
                } else {
                    Timber.e("Failed to sync track: %d", response.code)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error syncing track")
            }
        }
    }

    private fun sendPointSync(trackId: String, latLng: LatLng, altitude: Double?, accuracy: Float?) {
        try {
            val json = JSONObject().apply {
                put("lat", latLng.latitude)
                put("lon", latLng.longitude)
                put("timestamp", System.currentTimeMillis())
                altitude?.let { put("altitude", it) }
                accuracy?.let { put("accuracy", it.toDouble()) }
            }

            val request = createSignedRequest("$serverUrl/api/tracks/$trackId/points", json.toString())
            client.newCall(request).execute()
        } catch (e: Exception) {
            Timber.e(e, "Error sending sync point")
        }
    }

    fun sendPoint(latLng: LatLng, altitude: Double? = null, accuracy: Float? = null) {
        val trackId = currentTrackId ?: return

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("lat", latLng.latitude)
                    put("lon", latLng.longitude)
                    put("timestamp", System.currentTimeMillis())
                    altitude?.let { put("altitude", it) }
                    accuracy?.let { put("accuracy", it.toDouble()) }
                }

                val request = createSignedRequest("$serverUrl/api/tracks/$trackId/points", json.toString())

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Timber.d("Point sent: %f, %f", latLng.latitude, latLng.longitude)
                } else {
                    Timber.e("Failed to send point: %d", response.code)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error sending point")
            }
        }
    }

    fun stopTrack() {
        val trackId = currentTrackId ?: return

        scope.launch {
            try {
                val request = createSignedRequest("$serverUrl/api/tracks/$trackId/stop", "{}", "PUT")

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Timber.d("Track stopped: %s", trackId)
                    currentTrackId = null
                } else {
                    Timber.e("Failed to stop track: %d", response.code)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping track")
            }
        }
    }
}
