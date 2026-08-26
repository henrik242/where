package no.synth.where.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.ui.map.TrackColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendTrackStoreTest {

    private fun msg(json: String) = Json.parseToJsonElement(json).jsonObject

    private data class RenderedFeature(val clientId: String, val color: String, val geometry: String)

    private fun features(store: FriendTrackStore): List<RenderedFeature> {
        val geoJson = store.geoJson() ?: return emptyList()
        return Json.parseToJsonElement(geoJson).jsonObject.getValue("features").jsonArray
            .map { it.jsonObject }
            .map { feature ->
                val props = feature.getValue("properties").jsonObject
                RenderedFeature(
                    clientId = props.getValue("clientId").jsonPrimitive.content,
                    color = props.getValue("color").jsonPrimitive.content,
                    geometry = feature.getValue("geometry").jsonObject.getValue("type").jsonPrimitive.content
                )
            }
    }

    private fun update(clientId: String, trackId: String, lat: Double, lon: Double) = msg(
        """{"type":"track_update","userId":"$clientId","trackId":"$trackId",
           "name":"Tur","point":{"lat":$lat,"lon":$lon,"timestamp":1}}"""
    )

    @Test
    fun initialStateKeepsOneTrackPerClient() {
        val store = FriendTrackStore(listOf("aaa111", "bbb222", "ccc333"))
        store.accept(
            msg(
                """{"type":"initial_state","tracks":[
                  {"id":"t1","userId":"aaa111","name":"A","isActive":true,"color":"#FF5722",
                   "points":[{"lat":59.0,"lon":10.0},{"lat":59.1,"lon":10.1}]},
                  {"id":"t2","userId":"bbb222","name":"B","isActive":false,"color":"#2196F3",
                   "points":[{"lat":60.0,"lon":11.0}]}
                ]}"""
            )
        )
        val tracks = store.tracks()
        assertEquals(listOf("aaa111", "bbb222"), tracks.map { it.clientId })
        assertEquals(2, tracks.first { it.trackId == "t1" }.points.size)
        assertFalse(tracks.first { it.trackId == "t2" }.isActive)
    }

    @Test
    fun eachClientGetsItsOwnColorAndLabel() {
        val store = FriendTrackStore(listOf("aaa111", "bbb222"))
        store.accept(update("aaa111", "t1", 59.0, 10.0))
        store.accept(update("aaa111", "t1", 59.1, 10.1))
        store.accept(update("bbb222", "t2", 60.0, 11.0))

        val features = features(store)
        assertEquals(
            mapOf("aaa111" to TrackColors.forIndex(0), "bbb222" to TrackColors.forIndex(1)),
            features.associate { it.clientId to it.color }
        )
        // Two points for aaa111 means a line plus its endpoint; bbb222 only has the endpoint.
        assertEquals(1, features.count { it.geometry == "LineString" })
        assertEquals(2, features.count { it.geometry == "Point" })
    }

    @Test
    fun updatesFromUnfollowedClientsAreIgnored() {
        val store = FriendTrackStore(listOf("aaa111"))
        assertFalse(store.accept(update("zzz999", "t9", 59.0, 10.0)))
        assertTrue(store.tracks().isEmpty())
        assertNull(store.geoJson())
    }

    @Test
    fun serverSuppliedColorIsIgnored() {
        val store = FriendTrackStore(listOf("aaa111"))
        store.accept(
            msg("""{"type":"track_update","userId":"aaa111","trackId":"t1","color":"not-a-color",
                    "point":{"lat":59.0,"lon":10.0}}""")
        )
        assertEquals(listOf(TrackColors.forIndex(0)), features(store).map { it.color })
    }

    @Test
    fun unusableCoordinatesAreDropped() {
        val store = FriendTrackStore(listOf("aaa111"))
        store.accept(update("aaa111", "t1", 59.0, 10.0))
        // NaN would serialize to invalid JSON and take every followed friend off the map.
        store.accept(
            msg("""{"type":"track_update","userId":"aaa111","trackId":"t1",
                    "point":{"lat":"NaN","lon":"NaN"}}""")
        )
        store.accept(
            msg("""{"type":"track_update","userId":"aaa111","trackId":"t1",
                    "point":{"lat":999.0,"lon":10.0}}""")
        )
        assertEquals(1, store.tracks().first().points.size)
        assertNotNull(store.geoJson()?.let { Json.parseToJsonElement(it) })
    }

    @Test
    fun malformedInitialStateLeavesThePreviousSnapshot() {
        val store = FriendTrackStore(listOf("aaa111"))
        store.accept(update("aaa111", "t1", 59.0, 10.0))
        runCatching { store.accept(msg("""{"type":"initial_state","tracks":[1]}""")) }
        assertEquals(1, store.tracks().size)
    }

    @Test
    fun reactivationKeepsExistingPoints() {
        val store = FriendTrackStore(listOf("aaa111"))
        store.accept(update("aaa111", "t1", 59.0, 10.0))
        store.accept(update("aaa111", "t1", 59.1, 10.1))
        store.accept(msg("""{"type":"track_stopped","trackId":"t1"}"""))
        assertFalse(store.tracks().first().isActive)

        store.accept(msg("""{"type":"track_started","userId":"aaa111","trackId":"t1","name":"A"}"""))
        val track = store.tracks().first()
        assertTrue(track.isActive)
        assertEquals(2, track.points.size)
    }

    @Test
    fun trackCapIsPerClient() {
        val store = FriendTrackStore(listOf("aaa111", "bbb222"), maxTracksPerClient = 2)
        repeat(3) { store.accept(update("aaa111", "a$it", 59.0, 10.0)) }
        repeat(3) { store.accept(update("bbb222", "b$it", 60.0, 11.0)) }
        assertEquals(2, store.tracks().count { it.clientId == "aaa111" })
        assertEquals(2, store.tracks().count { it.clientId == "bbb222" })
    }

    @Test
    fun singlePointTrackStillGetsAnEndpointMarker() {
        val store = FriendTrackStore(listOf("aaa111"))
        store.accept(update("aaa111", "t1", 59.0, 10.0))
        val geoJson = store.geoJson().orEmpty()
        assertTrue(geoJson.contains(""""coordinates":[10.0,59.0]"""))
        assertFalse(geoJson.contains("LineString"))
    }

    @Test
    fun deletedTrackDisappears() {
        val store = FriendTrackStore(listOf("aaa111"))
        store.accept(update("aaa111", "t1", 59.0, 10.0))
        assertTrue(store.accept(msg("""{"type":"track_deleted","trackId":"t1"}""")))
        assertTrue(store.tracks().isEmpty())
    }

    @Test
    fun oldestPointsAreDroppedAtTheCap() {
        val store = FriendTrackStore(listOf("aaa111"), maxPointsPerTrack = 2)
        store.accept(update("aaa111", "t1", 59.0, 10.0))
        store.accept(update("aaa111", "t1", 59.1, 10.1))
        store.accept(update("aaa111", "t1", 59.2, 10.2))
        val points = store.tracks().first().points
        assertEquals(2, points.size)
        assertEquals(59.1, points.first().latitude)
        assertEquals(59.2, points.last().latitude)
    }
}
