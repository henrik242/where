package no.synth.where.ui.map

/** Where the top of the screen points, in degrees from true north, with the accuracy in degrees when known. */
data class CompassReading(val bearing: Double, val accuracy: Double? = null)

/**
 * A reading of [degrees] plus [correction] (declination, screen rotation), wrapped to 0..360.
 * Null when not finite, so one bad sample cannot reach the map.
 */
fun compassReading(degrees: Double, correction: Double = 0.0, accuracy: Double? = null): CompassReading? {
    val bearing = degrees + correction
    if (!bearing.isFinite()) return null
    return CompassReading(bearing.mod(360.0), accuracy?.takeIf { it.isFinite() })
}
