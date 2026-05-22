package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.bounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackBoundsTest {

    private fun trackOf(vararg latLngs: Pair<Double, Double>): Track = Track(
        name = "t",
        points = latLngs.mapIndexed { i, (lat, lng) ->
            TrackPoint(latLng = LatLng(lat, lng), timestamp = 1000L + i)
        },
        startTime = 1000L,
        endTime = 1000L + latLngs.size
    )

    @Test
    fun emptyTrackHasNoBounds() {
        assertNull(trackOf().bounds())
    }

    @Test
    fun singlePointTrackHasCollapsedBounds() {
        val b = trackOf(60.0 to 10.0).bounds()
        assertNotNull(b)
        assertEquals(60.0, b.south)
        assertEquals(60.0, b.north)
        assertEquals(10.0, b.west)
        assertEquals(10.0, b.east)
        assertTrue(b.isPoint)
    }

    @Test
    fun multiPointTrackHasRealBounds() {
        val b = trackOf(
            60.0 to 10.0,
            61.0 to 11.0,
            59.5 to 9.5,
        ).bounds()
        assertNotNull(b)
        assertEquals(59.5, b.south)
        assertEquals(61.0, b.north)
        assertEquals(9.5, b.west)
        assertEquals(11.0, b.east)
        assertEquals(60.25, b.center.latitude)
        assertEquals(10.25, b.center.longitude)
    }

    @Test
    fun identicalPointsCollapseToPointBounds() {
        val b = trackOf(60.0 to 10.0, 60.0 to 10.0).bounds()
        assertNotNull(b)
        assertTrue(b.isPoint)
    }

    @Test
    fun gpsJitterBelowEpsilonIsTreatedAsPoint() {
        // ~1 mm of jitter at 60°N — well under POINT_EPSILON (~1 cm)
        val b = trackOf(60.0 to 10.0, 60.00000001 to 10.00000001).bounds()
        assertNotNull(b)
        assertTrue(b.isPoint)
    }

    @Test
    fun pointsWithNaNAreDropped() {
        val b = listOf(
            LatLng(60.0, 10.0),
            LatLng(Double.NaN, 10.0),
            LatLng(61.0, Double.POSITIVE_INFINITY),
            LatLng(59.0, 9.0),
        ).bounds()
        assertNotNull(b)
        assertEquals(59.0, b.south)
        assertEquals(60.0, b.north)
        assertEquals(9.0, b.west)
        assertEquals(10.0, b.east)
    }

    @Test
    fun allNaNPointsReturnNull() {
        assertNull(listOf(LatLng(Double.NaN, Double.NaN)).bounds())
    }

    @Test
    fun listOfLatLngBoundsExtensionMatchesTrackBounds() {
        val points = listOf(LatLng(60.0, 10.0), LatLng(61.0, 11.0))
        val viaList = points.bounds()
        val viaTrack = trackOf(60.0 to 10.0, 61.0 to 11.0).bounds()
        assertEquals(viaList, viaTrack)
    }
}
