package no.synth.where.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompassReadingTest {

    private fun bearing(degrees: Double, correction: Double = 0.0) =
        compassReading(degrees, correction)?.bearing ?: -1.0

    @Test
    fun addsCorrection() {
        assertEquals(45.0, bearing(30.0, correction = 15.0), 1e-9)
    }

    @Test
    fun wrapsPastNorth() {
        assertEquals(85.0, bearing(350.0, correction = 95.0), 1e-9)
    }

    @Test
    fun westCorrectionWrapsBelowNorth() {
        assertEquals(357.0, bearing(2.0, correction = -5.0), 1e-9)
    }

    @Test
    fun keepsFiniteAccuracyOnly() {
        assertEquals(20.0, compassReading(0.0, accuracy = 20.0)?.accuracy ?: -1.0, 1e-9)
        assertNull(compassReading(0.0, accuracy = Double.NaN)?.accuracy)
    }

    @Test
    fun nonFiniteReadingIsDropped() {
        assertNull(compassReading(Double.NaN))
        assertNull(compassReading(10.0, correction = Double.POSITIVE_INFINITY))
    }
}
