package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun emptyListReturnsNull() {
        assertNull(TrackUtils.findTappedTrack(LatLng(60.0, 10.0), emptyList(), 80.0))
    }

    @Test
    fun singlePointTrackReturnsNull() {
        assertNull(TrackUtils.findTappedTrack(LatLng(60.0, 10.0), listOf(track(60.0 to 10.0)), 80.0))
    }

    @Test
    fun tapOnVertexReturnsTrack() {
        assertSame(line, TrackUtils.findTappedTrack(LatLng(60.0, 10.0), listOf(line), 5.0))
    }

    @Test
    fun tapMidSegmentReturnsTrack() {
        // Midpoint of the west-east segment, essentially on the line.
        assertSame(line, TrackUtils.findTappedTrack(LatLng(60.0, 10.005), listOf(line), 5.0))
    }

    @Test
    fun tapClearlyOffLineReturnsNull() {
        // ~1km north of the segment.
        assertNull(TrackUtils.findTappedTrack(LatLng(60.009, 10.005), listOf(line), 80.0))
    }

    @Test
    fun distanceToSegmentIsPerpendicular() {
        // ~111m north of the mid-segment; perpendicular distance should be ~111m.
        val d = TrackUtils.minDistanceToTrackMeters(LatLng(60.001, 10.005), line)
        assertTrue(d in 100.0..125.0, "expected ~111m but was $d")
    }

    @Test
    fun metersPerPixelAtEquatorZoomZero() {
        assertEquals(156543.03392, TrackUtils.metersPerPixel(0.0, 0.0), 0.01)
    }

    @Test
    fun metersPerPixelShrinksWithLatitude() {
        // cos(60deg) = 0.5, so ~half the equator resolution at the same zoom.
        val equator = TrackUtils.metersPerPixel(0.0, 5.0)
        val at60 = TrackUtils.metersPerPixel(60.0, 5.0)
        assertEquals(equator * 0.5, at60, equator * 0.001)
    }

    @Test
    fun metersPerPixelHalvesPerZoomLevel() {
        assertEquals(
            TrackUtils.metersPerPixel(0.0, 10.0) / 2.0,
            TrackUtils.metersPerPixel(0.0, 11.0),
            1e-6,
        )
    }
}
