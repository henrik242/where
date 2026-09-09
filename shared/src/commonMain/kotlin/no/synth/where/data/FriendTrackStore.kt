package no.synth.where.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.ui.map.TrackColors

/** One live track belonging to a followed client, as pushed over the tracking WebSocket. */
data class FriendTrack(
    val clientId: String,
    val trackId: String,
    val points: List<LatLng>,
    val isActive: Boolean
)

/**
 * Accumulates the live tracks of the followed clients from WebSocket frames and renders them as one
 * GeoJSON FeatureCollection. Every frame carries the owning client in `userId`, which is what lets
 * several followed friends get their own color and label.
 */
internal class FriendTrackStore(
    private val clientIds: List<String>,
    private val nicknames: Map<String, String> = emptyMap(),
    private val maxTracksPerClient: Int = MAX_TRACKS_PER_CLIENT,
    private val maxPointsPerTrack: Int = MAX_POINTS_PER_TRACK,
) {
    companion object {
        const val MAX_TRACKS_PER_CLIENT = 20
        const val MAX_POINTS_PER_TRACK = 10_000
    }

    private var tracks = LinkedHashMap<String, FriendTrack>()
    private var sharedPoints = LinkedHashMap<String, SharedPoint>()

    fun tracks(): List<FriendTrack> = tracks.values.toList()

    fun sharedPoints(): List<SharedPoint> = sharedPoints.values.toList()

    /** Apply one server frame; returns true when something changed and the state should be re-emitted. */
    fun accept(msg: JsonObject): Boolean = when (msg.str("type")) {
        "initial_state" -> initialState(msg)
        "track_started" -> trackStarted(msg)
        "track_update" -> trackUpdate(msg)
        "track_stopped" -> setActive(msg.str("trackId"), active = false)
        "track_deleted" -> trackDeleted(msg)
        "point_shared" -> pointShared(msg)
        "point_removed" -> pointRemoved(msg)
        else -> false
    }

    // Built into a local map so a malformed entry leaves the previous snapshot on screen.
    private fun initialState(msg: JsonObject): Boolean {
        val parsed = LinkedHashMap<String, FriendTrack>()
        for (element in msg["tracks"]?.jsonArray.orEmpty()) {
            val track = element.jsonObject
            val clientId = track.str("userId")?.takeIf { it in clientIds } ?: continue
            val trackId = track.str("id") ?: continue
            if (parsed.values.count { it.clientId == clientId } >= maxTracksPerClient) continue
            parsed[trackId] = FriendTrack(
                clientId = clientId,
                trackId = trackId,
                // Keep the newest points: a long history is trimmed at the tail, like live updates.
                points = parsePoints(track["points"]?.jsonArray).takeLast(maxPointsPerTrack),
                isActive = track.str("isActive")?.toBooleanStrictOrNull() ?: true,
            )
        }
        tracks = parsed
        // Shared points ride the same initial snapshot; rebuild them into a local map too.
        val parsedPoints = LinkedHashMap<String, SharedPoint>()
        for (element in msg["points"]?.jsonArray.orEmpty()) {
            parseSharedPoint(element.jsonObject)?.let { parsedPoints[it.id] = it }
        }
        sharedPoints = parsedPoints
        return true
    }

    private fun pointShared(msg: JsonObject): Boolean {
        val point = parseSharedPoint(msg["point"]?.jsonObject ?: return false) ?: return false
        sharedPoints[point.id] = point
        return true
    }

    private fun pointRemoved(msg: JsonObject): Boolean {
        val id = msg.str("id") ?: return false
        return sharedPoints.remove(id) != null
    }

    private fun parseSharedPoint(obj: JsonObject): SharedPoint? {
        val id = obj.str("id") ?: return null
        val clientId = obj.str("userId")?.takeIf { it in clientIds } ?: return null
        val lat = obj.str("lat")?.toDoubleOrNull() ?: return null
        val lon = obj.str("lon")?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return SharedPoint(
            id = id,
            ownerClientId = clientId,
            name = obj.str("name") ?: "",
            description = obj.str("description") ?: "",
            latLng = LatLng(lat, lon),
            color = obj.str("color") ?: "#FF5722",
            timestamp = obj.str("timestamp")?.toLongOrNull() ?: 0L,
        )
    }

    private fun trackStarted(msg: JsonObject): Boolean {
        val clientId = msg.str("userId")?.takeIf { it in clientIds } ?: return false
        val trackId = msg.str("trackId") ?: return false
        // The server re-sends track_started when a stale track resumes, so keep the points we have.
        if (tracks.containsKey(trackId)) return setActive(trackId, active = true)
        if (countFor(clientId) >= maxTracksPerClient) return false
        tracks[trackId] = FriendTrack(
            clientId = clientId,
            trackId = trackId,
            points = listOfNotNull(msg["point"]?.jsonObject?.let(::parsePoint)),
            isActive = true,
        )
        return true
    }

    private fun trackUpdate(msg: JsonObject): Boolean {
        val clientId = msg.str("userId")?.takeIf { it in clientIds } ?: return false
        val trackId = msg.str("trackId") ?: return false
        val point = msg["point"]?.jsonObject?.let(::parsePoint) ?: return false
        val existing = tracks[trackId]
        if (existing != null) {
            val points = if (existing.points.size >= maxPointsPerTrack) {
                existing.points.drop(1) + point
            } else {
                existing.points + point
            }
            tracks[trackId] = existing.copy(points = points, isActive = true)
            return true
        }
        if (countFor(clientId) >= maxTracksPerClient) return false
        tracks[trackId] = FriendTrack(clientId, trackId, listOf(point), isActive = true)
        return true
    }

    private fun trackDeleted(msg: JsonObject): Boolean {
        val trackId = msg.str("trackId") ?: return false
        return tracks.remove(trackId) != null
    }

    private fun setActive(trackId: String?, active: Boolean): Boolean {
        val track = trackId?.let { tracks[it] } ?: return false
        if (track.isActive == active) return false
        tracks[trackId] = track.copy(isActive = active)
        return true
    }

    private fun countFor(clientId: String): Int = tracks.values.count { it.clientId == clientId }

    /**
     * A dashed LineString per track, plus one endpoint marker per client, each carrying `clientId`
     * (the map label), `color` and `active` so a single data-driven layer can draw everyone at once.
     * The values are ours, not the server's: clientId is one of the followed ids, color is its list
     * position.
     */
    fun geoJson(): String? {
        val drawable = tracks.values.filter { it.points.isNotEmpty() }
        if (drawable.isEmpty()) return null
        val sb = StringBuilder()
        sb.append("""{"type":"FeatureCollection","features":[""")
        var first = true
        for (track in drawable) {
            if (track.points.size < 2) continue
            if (!first) sb.append(",")
            first = false
            sb.append("""{"type":"Feature",${props(track)},"geometry":{"type":"LineString","coordinates":[""")
            track.points.forEachIndexed { i, p ->
                if (i > 0) sb.append(",")
                sb.append("[${p.longitude},${p.latitude}]")
            }
            sb.append("]}}")
        }
        // One marker per client, not per track: a friend who stops and restarts recording would
        // otherwise pile up identical dots and names, and their halos would composite into a blob.
        for (track in drawable.groupBy { it.clientId }.values.map(::markerTrack)) {
            val last = track.points.last()
            if (!first) sb.append(",")
            first = false
            sb.append("""{"type":"Feature",${props(track)},"geometry":{"type":"Point","coordinates":[${last.longitude},${last.latitude}]}}""")
        }
        sb.append("]}")
        return sb.toString()
    }

    /**
     * A Point feature per shared point, each carrying `id`, `name` (quoted user text), `color` and
     * the owning `clientId`, so one data-driven layer draws them all with the owner's palette color.
     */
    fun sharedPointsGeoJson(): String? {
        if (sharedPoints.isEmpty()) return null
        val sb = StringBuilder()
        sb.append("""{"type":"FeatureCollection","features":[""")
        var first = true
        for (point in sharedPoints.values) {
            if (!first) sb.append(",")
            first = false
            val name = JsonPrimitive(point.name)
            // Colored by the owner's palette position, matching their track line, so a follower can
            // tell whose point it is at a glance (two friends' "Bilen" don't look identical).
            val color = TrackColors.forIndex(clientIds.indexOf(point.ownerClientId))
            sb.append("""{"type":"Feature","properties":{"id":${JsonPrimitive(point.id)},"name":$name,"color":"$color","clientId":"${point.ownerClientId}"},""")
            sb.append(""""geometry":{"type":"Point","coordinates":[${point.latLng.longitude},${point.latLng.latitude}]}}""")
        }
        sb.append("]}")
        return sb.toString()
    }

    // Neither tracks nor points carry a timestamp, so "newest" can only mean last in insertion
    // order, which initial_state rebuilds in the server's order. An active track wins regardless.
    private fun markerTrack(clientTracks: List<FriendTrack>): FriendTrack =
        clientTracks.lastOrNull { it.isActive } ?: clientTracks.last()

    private fun props(track: FriendTrack): String {
        val color = TrackColors.forIndex(clientIds.indexOf(track.clientId))
        // `label` is what the map draws: the local nickname when set, else the client id. User text,
        // so quote it via JsonPrimitive (it adds the quotes) or a stray char breaks the collection.
        val label = JsonPrimitive(nicknames[track.clientId] ?: track.clientId)
        return """"properties":{"clientId":"${track.clientId}","label":$label,"color":"$color","active":${track.isActive}}"""
    }

    private fun parsePoints(array: JsonArray?): List<LatLng> =
        array.orEmpty().mapNotNull { parsePoint(it.jsonObject) }

    // NaN and out-of-range coordinates are dropped: NaN serializes to invalid JSON, which would
    // take down the whole FeatureCollection, i.e. every followed friend.
    private fun parsePoint(obj: JsonObject): LatLng? {
        val lat = obj.str("lat")?.toDoubleOrNull() ?: return null
        val lon = obj.str("lon")?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return LatLng(lat, lon)
    }
}

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
