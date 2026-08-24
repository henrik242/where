package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class MapTapResolverTest {

    private val zoom = 15.0
    private val tap = LatLng(60.0, 10.0)

    private fun point(description: String) = SavedPoint(
        id = "p1",
        name = "Teltplass",
        latLng = tap,
        description = description,
        timestamp = 0L
    )

    private val line = Track(
        id = "t1",
        name = "T",
        points = listOf(
            TrackPoint(LatLng(60.0, 10.0), timestamp = 0L),
            TrackPoint(LatLng(60.0, 10.01), timestamp = 0L)
        ),
        startTime = 0L
    )

    @Test
    fun pointWinsOverATrackUnderTheSameTap() {
        val target = resolveMapTap(tap, zoom, listOf(point("")), listOf(line), null)
        assertEquals(MapTapTarget.Point(point("")), target)
    }

    @Test
    fun resolvesThePointAsItIsNow() {
        val target = resolveMapTap(tap, zoom, listOf(point("god teltplass")), emptyList(), null)
        assertEquals("god teltplass", (target as MapTapTarget.Point).point.description)
    }

    @Test
    fun tapOnTheLineResolvesTheTrack() {
        val target = resolveMapTap(LatLng(60.0, 10.005), zoom, emptyList(), listOf(line), null)
        assertEquals(MapTapTarget.TrackLine("t1"), target)
    }

    @Test
    fun navigatedRouteIsTappableToo() {
        val target = resolveMapTap(LatLng(60.0, 10.005), zoom, emptyList(), emptyList(), line)
        assertEquals(MapTapTarget.TrackLine("t1"), target)
    }

    @Test
    fun tapAwayFromTappableTracksClearsFocus() {
        val target = resolveMapTap(LatLng(61.0, 11.0), zoom, emptyList(), listOf(line), null)
        assertEquals(MapTapTarget.OutsideTracks, target)
    }

    @Test
    fun tapWithNothingOnTheMapIsUnhandled() {
        val target = resolveMapTap(tap, zoom, emptyList(), emptyList(), null)
        assertEquals(MapTapTarget.Nothing, target)
    }
}
