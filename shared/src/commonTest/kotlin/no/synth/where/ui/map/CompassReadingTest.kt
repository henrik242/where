package no.synth.where.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import org.maplibre.compose.location.HeadingMeasurement
import org.maplibre.compose.location.HeadingReference
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.degrees

class CompassReadingTest {

    private fun measurement(
        degrees: Double,
        reference: HeadingReference = HeadingReference.TrueNorth,
        accuracy: Double? = null,
    ) = HeadingMeasurement(
        bearing = Bearing.North + degrees.degrees,
        reference = reference,
        accuracy = accuracy?.degrees,
        measuredAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun trueHeadingIgnoresDeclination() {
        assertEquals(30.0, measurement(30.0).toCompassReading(0, declination = 10.0)?.bearing ?: -1.0, 1e-9)
    }

    @Test
    fun magneticHeadingAddsEastDeclination() {
        val reading = measurement(30.0, HeadingReference.MagneticNorth).toCompassReading(0, declination = 15.0)
        assertEquals(45.0, reading?.bearing ?: -1.0, 1e-9)
    }

    @Test
    fun magneticHeadingWithoutDeclinationIsUncorrected() {
        val reading = measurement(30.0, HeadingReference.MagneticNorth).toCompassReading(0, declination = null)
        assertEquals(30.0, reading?.bearing ?: -1.0, 1e-9)
    }

    @Test
    fun screenRotationAndDeclinationWrapPastNorth() {
        val reading = measurement(350.0, HeadingReference.MagneticNorth).toCompassReading(90, declination = 5.0)
        assertEquals(85.0, reading?.bearing ?: -1.0, 1e-9)
    }

    @Test
    fun westDeclinationWrapsBelowNorth() {
        val reading = measurement(2.0, HeadingReference.MagneticNorth).toCompassReading(0, declination = -5.0)
        assertEquals(357.0, reading?.bearing ?: -1.0, 1e-9)
    }

    @Test
    fun keepsAccuracy() {
        assertEquals(20.0, measurement(0.0, accuracy = 20.0).toCompassReading(0, null)?.accuracy ?: -1.0, 1e-9)
    }

    @Test
    fun nonFiniteReadingIsDropped() {
        assertNull(measurement(Double.NaN).toCompassReading(0, null))
        assertNull(measurement(10.0, HeadingReference.MagneticNorth).toCompassReading(0, Double.POSITIVE_INFINITY))
    }
}
