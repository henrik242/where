package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.synth.where.data.geo.LatLng
import no.synth.where.util.HmacUtils
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

class OnlineTrackingClient(
    private val serverUrl: String,
    private val clientId: String,
    private val hmacSecret: String,
    val client: HttpClient = createDefaultHttpClient()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentTrackId: String? = null

    fun startTrack(trackName: String) {
        scope.launch {
            try {
                val jsonBody = buildJsonObject {
                    put("userId", clientId)
                    put("name", trackName)
                }.toString()

                val signature = HmacUtils.generateSignature(jsonBody, hmacSecret)

                val response = client.post("$serverUrl/api/tracks") {
                    header("X-Client-Id", clientId)
                    header("X-Signature", signature)
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
                if (response.status.value in 200..299) {
                    val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val trackId = responseJson["id"]!!.jsonPrimitive.content
                    currentTrackId = trackId
                    Logger.d("Track started: %s", trackId)
                } else {
                    Logger.e("Failed to start track: %d", response.status.value)
                }
            } catch (e: Exception) {
                Logger.e(e, "Error starting track")
            }
        }
    }

    fun syncExistingTrack(track: Track) {
        scope.launch {
            try {
                val jsonBody = buildJsonObject {
                    put("userId", clientId)
                    put("name", track.name)
                    if (track.points.isNotEmpty()) {
                        put("points", buildJsonArray {
                            add(buildJsonObject {
                                put("lat", track.points[0].latLng.latitude)
                                put("lon", track.points[0].latLng.longitude)
                                put("timestamp", track.points[0].timestamp)
                                track.points[0].altitude?.let { put("altitude", it) }
                                track.points[0].accuracy?.let { put("accuracy", it.toDouble()) }
                            })
                        })
                    }
                }.toString()

                val signature = HmacUtils.generateSignature(jsonBody, hmacSecret)

                val response = client.post("$serverUrl/api/tracks") {
                    header("X-Client-Id", clientId)
                    header("X-Signature", signature)
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
                if (response.status.value in 200..299) {
                    val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val trackId = responseJson["id"]!!.jsonPrimitive.content
                    currentTrackId = trackId
                    Logger.d("Track synced: %s", trackId)

                    track.points.drop(1).forEach { point ->
                        sendPointSync(trackId, point.latLng, point.altitude, point.accuracy)
                    }
                } else {
                    Logger.e("Failed to sync track: %d", response.status.value)
                }
            } catch (e: Exception) {
                Logger.e(e, "Error syncing track")
            }
        }
    }

    private suspend fun sendPointSync(trackId: String, latLng: LatLng, altitude: Double?, accuracy: Float?) {
        try {
            val jsonBody = buildJsonObject {
                put("lat", latLng.latitude)
                put("lon", latLng.longitude)
                put("timestamp", currentTimeMillis())
                altitude?.let { put("altitude", it) }
                accuracy?.let { put("accuracy", it.toDouble()) }
            }.toString()

            val signature = HmacUtils.generateSignature(jsonBody, hmacSecret)

            client.post("$serverUrl/api/tracks/$trackId/points") {
                header("X-Client-Id", clientId)
                header("X-Signature", signature)
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
        } catch (e: Exception) {
            Logger.e(e, "Error sending sync point")
        }
    }

    fun sendPoint(latLng: LatLng, altitude: Double? = null, accuracy: Float? = null) {
        val trackId = currentTrackId ?: return

        scope.launch {
            try {
                val jsonBody = buildJsonObject {
                    put("lat", latLng.latitude)
                    put("lon", latLng.longitude)
                    put("timestamp", currentTimeMillis())
                    altitude?.let { put("altitude", it) }
                    accuracy?.let { put("accuracy", it.toDouble()) }
                }.toString()

                val signature = HmacUtils.generateSignature(jsonBody, hmacSecret)

                val response = client.post("$serverUrl/api/tracks/$trackId/points") {
                    header("X-Client-Id", clientId)
                    header("X-Signature", signature)
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
                if (response.status.value in 200..299) {
                    Logger.d("Point sent: %f, %f", latLng.latitude, latLng.longitude)
                } else {
                    Logger.e("Failed to send point: %d", response.status.value)
                }
            } catch (e: Exception) {
                Logger.e(e, "Error sending point")
            }
        }
    }

    fun stopTrack() {
        val trackId = currentTrackId ?: return

        scope.launch {
            try {
                val body = "{}"
                val signature = HmacUtils.generateSignature(body, hmacSecret)

                val response = client.put("$serverUrl/api/tracks/$trackId/stop") {
                    header("X-Client-Id", clientId)
                    header("X-Signature", signature)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                if (response.status.value in 200..299) {
                    Logger.d("Track stopped: %s", trackId)
                    currentTrackId = null
                } else {
                    Logger.e("Failed to stop track: %d", response.status.value)
                }
            } catch (e: Exception) {
                Logger.e(e, "Error stopping track")
            }
        }
    }
}
