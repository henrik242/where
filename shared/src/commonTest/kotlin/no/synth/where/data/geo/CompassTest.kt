package no.synth.where.data.geo

import kotlin.test.Test
import kotlin.test.assertEquals

class CompassTest {

    private val origin = LatLng(60.0, 10.0)
    private val mPerLat = 111_320.0

    private fun compass(bearing: Double) = compassPoint8(bearing, "N", "E", "S", "W")

    @Test
    fun allEightPoints() {
        assertEquals("N", compass(0.0))
        assertEquals("NE", compass(45.0))
        assertEquals("E", compass(90.0))
        assertEquals("SE", compass(135.0))
        assertEquals("S", compass(180.0))
        assertEquals("SW", compass(225.0))
        assertEquals("W", compass(270.0))
        assertEquals("NW", compass(315.0))
    }

    @Test
    fun wrapsAndSnapsToNearestPoint() {
        assertEquals("N", compass(360.0))
        assertEquals("N", compass(-5.0))
        assertEquals("N", compass(22.0))    // just under the NE boundary at 22.5
        assertEquals("NE", compass(22.5))   // the boundary rounds up
        assertEquals("NE", compass(23.0))   // just over it
        assertEquals("N", compass(720.0))
        assertEquals("NE", compass(405.0))  // wraps past 360 to a non-north point
    }

    @Test
    fun localizedLetters() {
        // Norwegian uses Ø for east and V for west.
        assertEquals("NØ", compassPoint8(45.0, "N", "Ø", "S", "V"))
        assertEquals("SV", compassPoint8(225.0, "N", "Ø", "S", "V"))
    }

    @Test
    fun bearingCardinalDirections() {
        val north = LatLng(origin.latitude + 500.0 / mPerLat, origin.longitude)
        val south = LatLng(origin.latitude - 500.0 / mPerLat, origin.longitude)
        assertEquals(0.0, origin.bearingTo(north), 0.5)
        assertEquals(180.0, origin.bearingTo(south), 0.5)
        // East/west compared via the compass label to stay clear of cos(lat) scaling.
        val east = LatLng(origin.latitude, origin.longitude + 0.01)
        val west = LatLng(origin.latitude, origin.longitude - 0.01)
        assertEquals("E", compass(origin.bearingTo(east)))
        assertEquals("W", compass(origin.bearingTo(west)))
    }
}
