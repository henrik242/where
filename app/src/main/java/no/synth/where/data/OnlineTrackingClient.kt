package no.synth.where.data

import android.util.Log
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
import org.maplibre.android.geometry.LatLng
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
                    Log.d("OnlineTracking", "Track started: $trackId")
                } else {
                    Log.e("OnlineTracking", "Failed to start track: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("OnlineTracking", "Error starting track: $e", e)
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
                    Log.d("OnlineTracking", "Track synced: $trackId")

                    track.points.drop(1).forEach { point ->
                        sendPointSync(trackId, point.latLng, point.altitude, point.accuracy)
                    }
                } else {
                    Log.e("OnlineTracking", "Failed to sync track: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("OnlineTracking", "Error syncing track: $e", e)
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
            Log.e("OnlineTracking", "Error sending sync point: $e", e)
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
                    Log.d("OnlineTracking", "Point sent: ${latLng.latitude}, ${latLng.longitude}")
                } else {
                    Log.e("OnlineTracking", "Failed to send point: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("OnlineTracking", "Error sending point: $e", e)
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
                    Log.d("OnlineTracking", "Track stopped: $trackId")
                    currentTrackId = null
                } else {
                    Log.e("OnlineTracking", "Failed to stop track: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("OnlineTracking", "Error stopping track: $e", e)
            }
        }
    }
}

