package no.synth.where.ui.map

import org.maplibre.compose.location.HeadingMeasurement
import org.maplibre.compose.location.HeadingReference
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees

/** Where the top of the screen points, in degrees from true north, with the accuracy in degrees when known. */
data class CompassReading(val bearing: Double, val accuracy: Double? = null)

/**
 * Adds [screenRotationDegrees] so landscape points the right way, and [declination] (degrees east)
 * when the reading is magnetic. Null for a non-finite reading.
 */
fun HeadingMeasurement.toCompassReading(screenRotationDegrees: Int, declination: Double?): CompassReading? {
    val correction = if (reference == HeadingReference.MagneticNorth) declination ?: 0.0 else 0.0
    val degrees = (bearing - Bearing.North).inDegrees + screenRotationDegrees + correction
    if (!degrees.isFinite()) return null
    return CompassReading(degrees.mod(360.0), accuracy?.inDegrees?.takeIf { it.isFinite() })
}
