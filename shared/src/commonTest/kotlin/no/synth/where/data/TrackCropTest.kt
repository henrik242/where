package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrackCropTest {

    private fun trackOf(vararg latLngs: Pair<Double, Double>): Track = Track(
        id = "t1",
        name = "t",
        points = latLngs.mapIndexed { i, (lat, lng) ->
            TrackPoint(latLng = LatLng(lat, lng), timestamp = 1000L + i)
        },
        startTime = 1000L,
        endTime = 1000L + latLngs.size,
    )

    private val sample = trackOf(
        60.0 to 10.0,
        60.1 to 10.1,
        60.2 to 10.2,
        60.3 to 10.3,
        60.4 to 10.4,
    )

    @Test
    fun cumulativeDistancesStartAtZeroAndAreMonotonic() {
        val cum = sample.cumulativeDistances()
        assertEquals(5, cum.size)
        assertEquals(0.0, cum.first())
        for (i in 1 until cum.size) assertTrue(cum[i] > cum[i - 1])
    }

    @Test
    fun fullRangeReturnsSameInstance() {
        assertSame(sample, sample.cropped(0, sample.points.lastIndex))
    }

    @Test
    fun trimHeadKeepsTail() {
        val c = sample.cropped(2, 4)
        assertEquals(3, c.points.size)
        assertEquals(LatLng(60.2, 10.2), c.points.first().latLng)
        assertEquals(LatLng(60.4, 10.4), c.points.last().latLng)
        assertEquals(c.points.first().timestamp, c.startTime)
        assertEquals(c.points.last().timestamp, c.endTime)
        assertEquals("t1", c.id)
        assertEquals("t", c.name)
    }

    @Test
    fun trimTailKeepsHead() {
        val c = sample.cropped(0, 2)
        assertEquals(3, c.points.size)
        assertEquals(LatLng(60.0, 10.0), c.points.first().latLng)
        assertEquals(LatLng(60.2, 10.2), c.points.last().latLng)
    }

    @Test
    fun trimBothEnds() {
        val c = sample.cropped(1, 3)
        assertEquals(3, c.points.size)
        assertEquals(LatLng(60.1, 10.1), c.points.first().latLng)
        assertEquals(LatLng(60.3, 10.3), c.points.last().latLng)
    }

    @Test
    fun indicesAreCoercedToKeepAtLeastTwoPoints() {
        // end <= start is coerced (end -> start + 1) so the result always has >= 2 points
        val c = sample.cropped(3, 3)
        assertEquals(2, c.points.size)
        assertEquals(LatLng(60.3, 10.3), c.points.first().latLng)
        assertEquals(LatLng(60.4, 10.4), c.points.last().latLng)
    }

    @Test
    fun outOfRangeIndicesAreClamped() {
        val c = sample.cropped(-5, 99)
        assertEquals(sample.points.size, c.points.size)
    }

    @Test
    fun endBeforeStartIsCoerced() {
        // start clamps to last-1 (=3), end then coerces to start+1 (=4)
        val c = sample.cropped(4, 1)
        assertEquals(2, c.points.size)
        assertEquals(LatLng(60.3, 10.3), c.points.first().latLng)
        assertEquals(LatLng(60.4, 10.4), c.points.last().latLng)
    }

    @Test
    fun tooFewPointsReturnsSameInstance() {
        val single = trackOf(60.0 to 10.0)
        assertSame(single, single.cropped(0, 0))
    }

    @Test
    fun cumulativeDistancesAreNonDecreasingWithDuplicatePoints() {
        // A stationary GPS sample (identical consecutive points) yields a zero delta, so the series
        // is non-decreasing rather than strictly increasing.
        val t = trackOf(60.0 to 10.0, 60.0 to 10.0, 60.1 to 10.1)
        val cum = t.cumulativeDistances()
        assertEquals(0.0, cum[0])
        assertEquals(cum[0], cum[1])          // no movement between the duplicates
        assertTrue(cum[2] > cum[1])
    }

    @Test
    fun clampCropRangeKeepsAtLeastTwoPointsAndStaysInBounds() {
        assertEquals(0 to 4, clampCropRange(5, -3, 99))   // clamped to full valid range
        assertEquals(3 to 4, clampCropRange(5, 3, 3))     // end pushed to start+1
        assertEquals(3 to 4, clampCropRange(5, 4, 1))     // start clamped to last-1, end to start+1
        assertEquals(1 to 3, clampCropRange(5, 1, 3))     // already valid, unchanged
    }

    @Test
    fun nearestPointIndexFindsClosestAndClamps() {
        val cum = listOf(0.0, 10.0, 20.0, 30.0)
        assertEquals(0, nearestPointIndex(cum, 0.0))
        assertEquals(1, nearestPointIndex(cum, 9.0))     // closer to 10 than 0
        assertEquals(0, nearestPointIndex(cum, 5.0))     // tie -> lower index
        assertEquals(2, nearestPointIndex(cum, 24.0))    // closer to 20 than 30
        assertEquals(3, nearestPointIndex(cum, 100.0))   // past the end -> last
        assertEquals(0, nearestPointIndex(cum, -5.0))    // before the start -> first
        assertEquals(0, nearestPointIndex(emptyList(), 5.0))
    }

    @Test
    fun nearestPointIndexHitsExactVerticesAndSingleElement() {
        val cum = listOf(0.0, 10.0, 20.0, 30.0)
        assertEquals(1, nearestPointIndex(cum, 10.0))         // exact interior vertex
        assertEquals(3, nearestPointIndex(cum, 30.0))         // exact last vertex
        assertEquals(0, nearestPointIndex(listOf(0.0), 7.0))  // single element
    }
}
