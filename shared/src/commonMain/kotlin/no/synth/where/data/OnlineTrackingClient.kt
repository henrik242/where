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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlin.math.min

class OnlineTrackingClient(
    private val serverUrl: String,
    private val clientId: String,
    private val trackingHint: String,
    val client: HttpClient = createDefaultHttpClient(),
    private val canSend: () -> Boolean = { true },
    onViewerCountChanged: (Int) -> Unit = {}
) {

    companion object {
        private const val MAX_QUEUE_SIZE = 50_000
        private const val MAX_BACKOFF_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewerCountTracker = ViewerCountTracker(
        serverUrl, clientId, trackingHint, client, scope, onViewerCountChanged
    )
    private var currentTrackId: String? = null
    private var pendingTrackName: String? = null
    private var startTrackBackoffMs = 5_000L

    private data class QueuedPoint(
        val latLng: LatLng,
        val altitude: Double?,
        val accuracy: Float?,
        val timestamp: Long
    )

    private val pointQueue = mutableListOf<QueuedPoint>()
    private val queueMutex = Mutex()
    private var flushScheduled = false

    fun startTrack(trackName: String) {
        pendingTrackName = trackName
        startTrackBackoffMs = 5_000L
        scope.launch { createTrack(trackName) }
        viewerCountTracker.startPolling()
    }

    private suspend fun createTrack(trackName: String) {
        try {
            val jsonBody = buildJsonObject {
                put("userId", clientId)
                put("name", trackName)
            }.toString()

            val signature = HmacUtils.generateSignature(jsonBody, trackingHint)

            val response = client.post("$serverUrl/api/tracks") {
                header("X-Client-Id", clientId)
                header("X-Signature", signature)
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            if (response.status.value in 200..299) {
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val trackId = responseJson["id"]?.jsonPrimitive?.content ?: return
                currentTrackId = trackId
                pendingTrackName = null
                startTrackBackoffMs = 5_000L
                Logger.d("Track started: %s", trackId)
                flushQueue()
            } else {
                Logger.e("Failed to start track: %d", response.status.value)
                scheduleStartTrackRetry()
            }
        } catch (e: Exception) {
            Logger.e(e, "Error starting track")
            scheduleStartTrackRetry()
        }
    }

    private fun scheduleStartTrackRetry() {
        val name = pendingTrackName ?: return
        val backoff = startTrackBackoffMs
        startTrackBackoffMs = min(startTrackBackoffMs * 2, MAX_BACKOFF_MS)
        scope.launch {
            delay(backoff)
            createTrack(name)
        }
    }

    fun syncExistingTrack(track: Track) {
        viewerCountTracker.startPolling()
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

                val signature = HmacUtils.generateSignature(jsonBody, trackingHint)

                val response = client.post("$serverUrl/api/tracks") {
                    header("X-Client-Id", clientId)
                    header("X-Signature", signature)
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
                if (response.status.value in 200..299) {
                    val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val trackId = responseJson["id"]?.jsonPrimitive?.content ?: return@launch
                    currentTrackId = trackId
                    Logger.d("Track synced: %s", trackId)

                    for (point in track.points.drop(1)) {
                        val ok = sendPointSync(
                            trackId, point.latLng, point.altitude, point.accuracy, point.timestamp
                        )
                        if (!ok) {
                            val remaining = track.points.dropWhile { it !== point }
                            queueMutex.withLock {
                                val queued = remaining.map {
                                    QueuedPoint(it.latLng, it.altitude, it.accuracy, it.timestamp)
                                }
                                pointQueue.addAll(0, queued)
                                trimQueue()
                            }
                            scheduleRetryFlush()
                            return@launch
                        }
                    }
                    flushQueue()
                } else {
                    Logger.e("Failed to sync track: %d", response.status.value)
                    scheduleRetryFlush()
                }
            } catch (e: Exception) {
                Logger.e(e, "Error syncing track")
                scheduleRetryFlush()
            }
        }
    }

    private suspend fun sendPointSync(
        trackId: String, latLng: LatLng, altitude: Double?, accuracy: Float?, timestamp: Long
    ): Boolean {
        return try {
            val jsonBody = buildJsonObject {
                put("lat", latLng.latitude)
                put("lon", latLng.longitude)
                put("timestamp", timestamp)
                altitude?.let { put("altitude", it) }
                accuracy?.let { put("accuracy", it.toDouble()) }
            }.toString()

            val signature = HmacUtils.generateSignature(jsonBody, trackingHint)

            val response = client.post("$serverUrl/api/tracks/$trackId/points") {
                header("X-Client-Id", clientId)
                header("X-Signature", signature)
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            Logger.e(e, "Error sending sync point")
            false
        }
    }

    fun sendPoint(latLng: LatLng, altitude: Double? = null, accuracy: Float? = null) {
        val point = QueuedPoint(latLng, altitude, accuracy, currentTimeMillis())
        scope.launch {
            queueMutex.withLock {
                pointQueue.add(point)
                trimQueue()
            }
            flushQueue()
        }
    }

    private fun trimQueue() {
        if (pointQueue.size > MAX_QUEUE_SIZE) {
            val excess = pointQueue.size - MAX_QUEUE_SIZE
            Logger.w("Queue exceeded %d, dropping %d oldest points", MAX_QUEUE_SIZE, excess)
            pointQueue.subList(0, excess).clear()
        }
    }

    private suspend fun flushQueue() {
        val trackId = currentTrackId ?: return
        if (!canSend()) {
            scheduleRetryFlush()
            return
        }

        val points = queueMutex.withLock {
            val copy = pointQueue.toList()
            pointQueue.clear()
            copy
        }
        if (points.isEmpty()) return

        val failed = mutableListOf<QueuedPoint>()
        for (point in points) {
            try {
                val jsonBody = buildJsonObject {
                    put("lat", point.latLng.latitude)
                    put("lon", point.latLng.longitude)
                    put("timestamp", point.timestamp)
                    point.altitude?.let { put("altitude", it) }
                    point.accuracy?.let { put("accuracy", it.toDouble()) }
                }.toString()

                val signature = HmacUtils.generateSignature(jsonBody, trackingHint)

                val response = client.post("$serverUrl/api/tracks/$trackId/points") {
                    header("X-Client-Id", clientId)
                    header("X-Signature", signature)
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
                when {
                    response.status.value in 200..299 -> {
                        Logger.d("Point sent: %f, %f", point.latLng.latitude, point.latLng.longitude)
                    }
                    response.status.value == 404 -> {
                        Logger.w("Got 404 for track %s, server may have restarted", trackId)
                        val remaining = points.subList(points.indexOf(point), points.size)
                        queueMutex.withLock { pointQueue.addAll(0, failed + remaining) }
                        recreateTrack()
                        return
                    }
                    else -> {
                        Logger.e("Failed to send point: %d", response.status.value)
                        failed.add(point)
                    }
                }
            } catch (e: Exception) {
                Logger.e(e, "Error sending point")
                failed.add(point)
                val remaining = points.subList(points.indexOf(point) + 1, points.size)
                queueMutex.withLock { pointQueue.addAll(0, failed + remaining) }
                scheduleRetryFlush()
                return
            }
        }

        if (failed.isNotEmpty()) {
            queueMutex.withLock { pointQueue.addAll(0, failed) }
            scheduleRetryFlush()
        }
    }

    private suspend fun recreateTrack() {
        currentTrackId = null
        val name = pendingTrackName ?: "resumed-track"
        pendingTrackName = name
        startTrackBackoffMs = 5_000L
        createTrack(name)
    }

    private fun scheduleRetryFlush() {
        scope.launch {
            val shouldSchedule = queueMutex.withLock {
                if (flushScheduled) return@launch
                flushScheduled = true
                true
            }
            if (shouldSchedule) {
                delay(10_000)
                queueMutex.withLock { flushScheduled = false }
                flushQueue()
            }
        }
    }

    fun stopTrack() {
        viewerCountTracker.stopPolling()
        val trackId = currentTrackId ?: return

        scope.launch {
            flushQueue()

            val queueEmpty = queueMutex.withLock { pointQueue.isEmpty() }
            if (!queueEmpty) {
                Logger.w("Stopping track %s with %d points still queued", trackId, pointQueue.size)
                return@launch
            }

            try {
                val body = "{}"
                val signature = HmacUtils.generateSignature(body, trackingHint)

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
