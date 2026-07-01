package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrackUtilsTest {

    private fun track(vararg coords: Pair<Double, Double>) = Track(
        name = "T",
        points = coords.map { (lat, lng) -> TrackPoint(LatLng(lat, lng), timestamp = 0L) },
        startTime = 0L,
    )

    private val line = track(60.0 to 10.0, 60.0 to 10.01)

    @Test
    fun nullTrackReturnsNull() {
        assertNull(TrackUtils.findTappedTrack(LatLng(60.0, 10.0), null))
    }

    @Test
    fun singlePointTrackReturnsNull() {
        assertNull(TrackUtils.findTappedTrack(LatLng(60.0, 10.0), track(60.0 to 10.0)))
    }

    @Test
    fun tapOnVertexReturnsTrack() {
        assertSame(line, TrackUtils.findTappedTrack(LatLng(60.0, 10.0), line, maxDistanceMeters = 5.0))
    }

    @Test
    fun tapMidSegmentReturnsTrack() {
        // Midpoint of the west-east segment, essentially on the line.
        assertSame(line, TrackUtils.findTappedTrack(LatLng(60.0, 10.005), line, maxDistanceMeters = 5.0))
    }

    @Test
    fun tapClearlyOffLineReturnsNull() {
        // ~1km north of the segment.
        assertNull(TrackUtils.findTappedTrack(LatLng(60.009, 10.005), line, maxDistanceMeters = 80.0))
    }

    @Test
    fun distanceToSegmentIsPerpendicular() {
        // ~111m north of the mid-segment; perpendicular distance should be ~111m.
        val d = TrackUtils.minDistanceToTrackMeters(LatLng(60.001, 10.005), line)
        assertTrue(d in 100.0..125.0, "expected ~111m but was $d")
    }
}
