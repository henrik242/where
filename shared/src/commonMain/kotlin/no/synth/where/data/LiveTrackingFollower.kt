package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import kotlin.math.min

class LiveTrackingFollower(
    private val serverUrl: String
) {
    companion object {
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val MAX_POINTS_PER_TRACK = 10_000
        private const val MAX_TRACKS = 50
        private const val PING_INTERVAL_MS = 30_000L
        val CLIENT_ID_REGEX = Regex("^[a-z0-9]{6}$")
    }

    data class FriendTrack(
        val trackId: String,
        val name: String,
        val points: List<LatLng>,
        val isActive: Boolean,
        val color: String?
    )

    sealed class FollowState {
        data object Idle : FollowState()
        data class Connecting(val clientId: String) : FollowState()
        data class Following(
            val clientId: String,
            val tracks: List<FriendTrack>
        ) : FollowState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _state = MutableStateFlow<FollowState>(FollowState.Idle)
    val state: StateFlow<FollowState> = _state.asStateFlow()

    private val _friendTrackGeoJson = MutableStateFlow<String?>(null)
    val friendTrackGeoJson: StateFlow<String?> = _friendTrackGeoJson.asStateFlow()

    private var currentClientId: String? = null
    private var connectionJob: Job? = null
    private var session: WebSocketSession? = null
    private var wsClient: HttpClient? = null
    private val tracks = mutableMapOf<String, FriendTrack>()

    fun follow(clientId: String) {
        if (!CLIENT_ID_REGEX.matches(clientId)) return
        stopFollowing()
        currentClientId = clientId
        _state.value = FollowState.Connecting(clientId)
        connectionJob = scope.launch { connectWithRetry(clientId) }
    }

    fun stopFollowing() {
        val oldClientId = currentClientId
        currentClientId = null
        connectionJob?.cancel()
        connectionJob = null
        tracks.clear()
        _state.value = FollowState.Idle
        _friendTrackGeoJson.value = null
        if (oldClientId != null) {
            scope.launch {
                try {
                    session?.close()
                } catch (_: Exception) { }
                session = null
                wsClient?.close()
                wsClient = null
            }
        }
    }

    private suspend fun connectWithRetry(clientId: String) {
        var reconnectDelay = 1_000L
        while (currentClientId == clientId) {
            try {
                connect(clientId)
                // Connection was established and then closed normally — reset backoff
                reconnectDelay = 1_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e, "WebSocket connection error")
            }
            if (currentClientId != clientId) break
            _state.value = FollowState.Connecting(clientId)
            Logger.d("WebSocket reconnecting in %dms", reconnectDelay)
            delay(reconnectDelay)
            reconnectDelay = min(reconnectDelay * 2, MAX_RECONNECT_DELAY_MS)
        }
    }

    private suspend fun connect(clientId: String) {
        // Close previous client if any (e.g. from a prior failed connect() in the retry loop)
        wsClient?.close()

        val client = HttpClient {
            install(WebSockets) {
                pingIntervalMillis = PING_INTERVAL_MS
            }
        }
        wsClient = client

        val wsUrl = serverUrl.trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val ws = client.webSocketSession("$wsUrl/ws")
        session = ws

        val subscribeMsg = buildJsonObject {
            put("type", "subscribe")
            put("clients", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(clientId)) })
            put("historical", true)
        }.toString()
        ws.send(Frame.Text(subscribeMsg))

        for (frame in ws.incoming) {
            if (currentClientId != clientId) break
            if (frame is Frame.Text) {
                handleMessage(clientId, frame.readText())
            }
        }
    }

    private suspend fun handleMessage(clientId: String, text: String) {
        try {
            val msg = json.parseToJsonElement(text).jsonObject
            mutex.withLock {
                if (currentClientId != clientId) return
                when (msg["type"]?.jsonPrimitive?.content) {
                    "initial_state" -> handleInitialState(clientId, msg)
                    "track_started" -> handleTrackStarted(clientId, msg)
                    "track_update" -> handleTrackUpdate(clientId, msg)
                    "track_stopped" -> handleTrackStopped(clientId, msg)
                    "track_deleted" -> handleTrackDeleted(clientId, msg)
                }
            }
        } catch (e: Exception) {
            Logger.e(e, "Error parsing WebSocket message")
        }
    }

    private fun handleInitialState(clientId: String, msg: JsonObject) {
        tracks.clear()
        val trackArray = msg["tracks"]?.jsonArray ?: return
        for (trackElement in trackArray.take(MAX_TRACKS)) {
            val track = trackElement.jsonObject
            val trackId = track["id"]?.jsonPrimitive?.content ?: continue
            val points = parsePoints(track["points"]?.jsonArray).take(MAX_POINTS_PER_TRACK)
            tracks[trackId] = FriendTrack(
                trackId = trackId,
                name = track["name"]?.jsonPrimitive?.content ?: "Track",
                points = points,
                isActive = track["isActive"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                color = track["color"]?.jsonPrimitive?.content
            )
        }
        emitState(clientId)
    }

    private fun handleTrackStarted(clientId: String, msg: JsonObject) {
        val trackId = msg["trackId"]?.jsonPrimitive?.content ?: return
        if (tracks.size >= MAX_TRACKS) return
        val point = msg["point"]?.jsonObject?.let { parsePoint(it) }
        tracks[trackId] = FriendTrack(
            trackId = trackId,
            name = msg["name"]?.jsonPrimitive?.content ?: "Track",
            points = listOfNotNull(point),
            isActive = true,
            color = msg["color"]?.jsonPrimitive?.content
        )
        emitState(clientId)
    }

    private fun handleTrackUpdate(clientId: String, msg: JsonObject) {
        val trackId = msg["trackId"]?.jsonPrimitive?.content ?: return
        val point = msg["point"]?.jsonObject?.let { parsePoint(it) } ?: return
        val track = tracks[trackId]
        if (track != null) {
            val newPoints = if (track.points.size >= MAX_POINTS_PER_TRACK) {
                track.points.drop(1) + point
            } else {
                track.points + point
            }
            tracks[trackId] = track.copy(points = newPoints)
        } else {
            if (tracks.size >= MAX_TRACKS) return
            tracks[trackId] = FriendTrack(
                trackId = trackId,
                name = msg["name"]?.jsonPrimitive?.content ?: "Track",
                points = listOf(point),
                isActive = true,
                color = msg["color"]?.jsonPrimitive?.content
            )
        }
        emitState(clientId)
    }

    private fun handleTrackStopped(clientId: String, msg: JsonObject) {
        val trackId = msg["trackId"]?.jsonPrimitive?.content ?: return
        val track = tracks[trackId] ?: return
        tracks[trackId] = track.copy(isActive = false)
        emitState(clientId)
    }

    private fun handleTrackDeleted(clientId: String, msg: JsonObject) {
        val trackId = msg["trackId"]?.jsonPrimitive?.content ?: return
        tracks.remove(trackId)
        emitState(clientId)
    }

    private fun parsePoints(array: kotlinx.serialization.json.JsonArray?): List<LatLng> {
        if (array == null) return emptyList()
        return array.mapNotNull { element ->
            parsePoint(element.jsonObject)
        }
    }

    private fun parsePoint(obj: JsonObject): LatLng? {
        val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        return LatLng(lat, lon)
    }

    private fun emitState(clientId: String) {
        if (currentClientId != clientId) return
        val snapshot = tracks.values.toList()
        _state.value = FollowState.Following(clientId = clientId, tracks = snapshot)
        updateGeoJson(clientId, snapshot)
    }

    private fun updateGeoJson(clientId: String, trackList: List<FriendTrack>) {
        // Collect all points from all tracks
        val allPoints = trackList.flatMap { it.points }
        if (allPoints.isEmpty()) {
            _friendTrackGeoJson.value = null
            return
        }
        if (allPoints.size == 1) {
            val p = allPoints.first()
            val geoJson = """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"clientId":"$clientId"},"geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]}}]}"""
            _friendTrackGeoJson.value = geoJson
            return
        }
        // Build one LineString per track + endpoint per track
        val sb = StringBuilder()
        sb.append("""{"type":"FeatureCollection","features":[""")
        var first = true
        for (track in trackList) {
            if (track.points.isEmpty()) continue
            if (track.points.size >= 2) {
                if (!first) sb.append(",")
                first = false
                sb.append("""{"type":"Feature","properties":{"clientId":"$clientId"},"geometry":{"type":"LineString","coordinates":[""")
                track.points.forEachIndexed { i, p ->
                    if (i > 0) sb.append(",")
                    sb.append("[${p.longitude},${p.latitude}]")
                }
                sb.append("]}}")
            }
            // Endpoint marker
            val last = track.points.last()
            if (!first) sb.append(",")
            first = false
            sb.append("""{"type":"Feature","properties":{"clientId":"$clientId"},"geometry":{"type":"Point","coordinates":[${last.longitude},${last.latitude}]}}""")
        }
        sb.append("]}")
        val geoJson = sb.toString()
        _friendTrackGeoJson.value = geoJson
    }

    fun close() {
        stopFollowing()
        scope.cancel()
    }
}
