package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElevationProfileTest {

    // Points march east so cumulative distance grows monotonically.
    private fun track(altitudes: List<Double?>, spacingDeg: Double = 0.001) = Track(
        name = "T",
        points = altitudes.mapIndexed { i, alt ->
            TrackPoint(LatLng(60.0, 10.0 + i * spacingDeg), timestamp = i.toLong(), altitude = alt)
        },
        startTime = 0L,
    )

    @Test
    fun zeroAltitudePointsReturnsNull() {
        assertNull(track(listOf(null, null, null)).elevationProfileOrNull())
    }

    @Test
    fun singleAltitudePointReturnsNull() {
        assertNull(track(listOf(100.0, null, null)).elevationProfileOrNull())
    }

    @Test
    fun fewerThanTwoPointsReturnsNull() {
        assertNull(track(listOf(100.0)).elevationProfileOrNull())
    }

    @Test
    fun carryForwardFillsNullAltitudes() {
        val p = track(listOf(100.0, null, null, 140.0)).elevationProfileOrNull()!!
        assertEquals(listOf(100.0, 100.0, 100.0, 140.0), p.elevations)
        assertEquals(100.0, p.minEle)
        assertEquals(140.0, p.maxEle)
        assertEquals(40.0, p.gain, "only the single +40 rise counts")
    }

    @Test
    fun gainIgnoresDescents() {
        // 100 -> 200 (+100) -> 150 (drop, ignored) -> 180 (+30)
        val p = track(listOf(100.0, 200.0, 150.0, 180.0)).elevationProfileOrNull()!!
        assertEquals(130.0, p.gain)
    }

    @Test
    fun downsamplingCapsSamplesAndKeepsLastPoint() {
        val altitudes = (0 until 1000).map { it.toDouble() }
        val p = track(altitudes).elevationProfileOrNull(maxSamples = 10)!!
        assertTrue(p.distances.size <= 11, "expected <= maxSamples+1 but was ${p.distances.size}")
        // The final real point must always be retained as the last sample.
        assertEquals(p.totalDistance, p.distances.last(), 1e-6)
        assertEquals(999.0, p.elevations.last())
    }

    @Test
    fun minMaxComeFromDownsampledSet() {
        // Spike at index 950 falls between sampled indices (0,100,..,900,999) and is dropped.
        val altitudes = (0 until 1000).map { if (it == 950) 9999.0 else it.toDouble() }
        val p = track(altitudes).elevationProfileOrNull(maxSamples = 10)!!
        assertEquals(p.elevations.max(), p.maxEle)
        assertEquals(p.elevations.min(), p.minEle)
        assertTrue(p.maxEle < 9999.0, "the un-sampled spike must not define maxEle")
    }
}
