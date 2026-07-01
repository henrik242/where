package no.synth.where.data.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentProjectionTest {

    // Mid-Norway latitude for realistic metres-per-degree.
    private val lat = 60.0
    private val mPerLat = 111_320.0
    private val mPerLng = 111_320.0 * cos(lat * PI / 180.0)

    // An east-west segment ~200 m long centred on (60.0, 10.0).
    private val a = LatLng(lat, 10.0)
    private val b = LatLng(lat, 10.0 + 200.0 / mPerLng)
    private val mid = LatLng(lat, 10.0 + 100.0 / mPerLng)

    @Test
    fun midpointOnSegment() {
        val pr = projectOntoSegment(mid, a, b)
        assertEquals(0.5, pr.t, 0.02)
        assertTrue(pr.distanceMeters < 2.0, "expected ~0, was ${pr.distanceMeters}")
    }

    @Test
    fun perpendicularOffset() {
        // 50 m north of the midpoint.
        val offset = LatLng(lat + 50.0 / mPerLat, mid.longitude)
        val pr = projectOntoSegment(offset, a, b)
        assertEquals(0.5, pr.t, 0.02)
        assertEquals(50.0, pr.distanceMeters, 2.0)
    }

    @Test
    fun beyondEndpointsClamps() {
        val beforeA = LatLng(lat, 10.0 - 50.0 / mPerLng)
        val beyondB = LatLng(lat, b.longitude + 50.0 / mPerLng)
        assertEquals(0.0, projectOntoSegment(beforeA, a, b).t)
        assertEquals(1.0, projectOntoSegment(beyondB, a, b).t)
    }

    @Test
    fun degenerateSegment() {
        val p = LatLng(lat + 30.0 / mPerLat, 10.0)
        val pr = projectOntoSegment(p, a, a)
        assertEquals(0.0, pr.t)
        assertEquals(p.distanceTo(a), pr.distanceMeters, 2.0)
    }
}
