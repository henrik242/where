package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import no.synth.where.util.Logger
import kotlin.math.min

/**
 * Follows the live tracks of one or more other clients over the tracking server's WebSocket.
 * All followed clients share a single subscription; each one gets its own color and map label.
 */
class LiveTrackingFollower(
    private val serverUrl: String
) {
    companion object {
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val PING_INTERVAL_MS = 30_000L
        const val MAX_FOLLOWED = 5
        val CLIENT_ID_REGEX = Regex("^[a-z0-9]{6}$")

        /** Valid, deduplicated ids, capped at [MAX_FOLLOWED]. */
        fun sanitize(clientIds: List<String>): List<String> =
            clientIds.distinct().filter { CLIENT_ID_REGEX.matches(it) }.take(MAX_FOLLOWED)
    }

    sealed class FollowState {
        data object Idle : FollowState()
        data object Connecting : FollowState()
        data class Following(val tracks: List<FriendTrack>) : FollowState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<FollowState>
        field = MutableStateFlow<FollowState>(FollowState.Idle)

    val friendTrackGeoJson: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    // Shared points (markers) dropped by followed clients, as a GeoJSON FeatureCollection, plus the
    // parsed list so a tap can offer "save locally".
    val friendPointsGeoJson: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val friendPoints: StateFlow<List<SharedPoint>>
        field = MutableStateFlow<List<SharedPoint>>(emptyList())

    private var currentClientIds: List<String> = emptyList()
    private var currentNicknames: Map<String, String> = emptyMap()
    private var connectionJob: Job? = null

    /**
     * Follow exactly [clientIds], labelling each with [nicknames]. Changing the set (or a label)
     * reconnects and the server replies with a fresh snapshot; the overlay keeps the previous
     * tracks until it arrives so the map doesn't blink. A label edit is rare, so reconnecting to
     * relabel keeps the store single-threaded rather than mutating it across coroutines.
     */
    fun follow(clientIds: List<String>, nicknames: Map<String, String> = emptyMap()) {
        val ids = sanitize(clientIds)
        if (ids.isEmpty()) {
            stopFollowing()
            return
        }
        // Only the labels of followed ids matter; ignore names for ids we don't follow.
        val relevant = nicknames.filterKeys { it in ids }
        if (ids == currentClientIds && relevant == currentNicknames && connectionJob?.isActive == true) return
        connectionJob?.cancel()
        currentClientIds = ids
        currentNicknames = relevant
        state.value = FollowState.Connecting
        // A store per connection generation: a cancelled connection cannot write into the new set.
        connectionJob = scope.launch { connectWithRetry(ids, FriendTrackStore(ids, relevant)) }
    }

    fun stopFollowing() {
        connectionJob?.cancel()
        connectionJob = null
        currentClientIds = emptyList()
        currentNicknames = emptyMap()
        friendTrackGeoJson.value = null
        friendPointsGeoJson.value = null
        friendPoints.value = emptyList()
        state.value = FollowState.Idle
    }

    private suspend fun connectWithRetry(clientIds: List<String>, store: FriendTrackStore) {
        var reconnectDelay = 1_000L
        while (currentClientIds == clientIds) {
            try {
                connect(clientIds, store)
                // Connection was established and then closed normally — reset backoff
                reconnectDelay = 1_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e, "WebSocket connection error")
            }
            if (currentClientIds != clientIds) break
            state.value = FollowState.Connecting
            Logger.d("WebSocket reconnecting in %dms", reconnectDelay)
            delay(reconnectDelay)
            reconnectDelay = min(reconnectDelay * 2, MAX_RECONNECT_DELAY_MS)
        }
    }

    // The client is owned by this coroutine and closed in finally, including on cancellation, so
    // no connection generation can ever close another one's socket.
    private suspend fun connect(clientIds: List<String>, store: FriendTrackStore) {
        val client = HttpClient {
            install(WebSockets) {
                pingIntervalMillis = PING_INTERVAL_MS
            }
        }
        try {
            val wsUrl = serverUrl.trimEnd('/')
                .replace("https://", "wss://")
                .replace("http://", "ws://")
            val ws = client.webSocketSession("$wsUrl/ws")

            val subscribeMsg = buildJsonObject {
                put("type", "subscribe")
                put("clients", buildJsonArray { clientIds.forEach { add(JsonPrimitive(it)) } })
                put("historical", true)
            }.toString()
            ws.send(Frame.Text(subscribeMsg))

            for (frame in ws.incoming) {
                if (currentClientIds != clientIds) break
                if (frame is Frame.Text) {
                    handleMessage(clientIds, store, frame.readText())
                }
            }
        } finally {
            client.close()
        }
    }

    private fun handleMessage(clientIds: List<String>, store: FriendTrackStore, text: String) {
        try {
            val msg = AppJson.parseToJsonElement(text).jsonObject
            if (currentClientIds != clientIds) return
            if (store.accept(msg)) {
                state.value = FollowState.Following(store.tracks())
                friendTrackGeoJson.value = store.geoJson()
                friendPointsGeoJson.value = store.sharedPointsGeoJson()
                friendPoints.value = store.sharedPoints()
            }
        } catch (e: Exception) {
            Logger.e(e, "Error parsing WebSocket message")
        }
    }

    fun close() {
        stopFollowing()
        scope.cancel()
    }
}
