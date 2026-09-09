package no.synth.where.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.synth.where.data.geo.HeadingBreakdown

/**
 * Everything the heading pipeline knows at one instant, for the on-map readout.
 *
 * A heading that disagrees with the world can fail in three different places, and they are only
 * distinguishable side by side: the posture choice picking the wrong reference, the sensor's own
 * magnetic reference being dragged off north, or the direction of travel simply differing from
 * where the phone points. Guessing between them from a photograph of the map does not work.
 */
data class HeadingDebugSnapshot(
    val breakdown: HeadingBreakdown,
    /**
     * The magnetometer's own confidence, as SensorManager's SENSOR_STATUS constants: 3 high,
     * 2 medium, 1 low, 0 unreliable, -1 no contact. Anything below high is the sensor saying its
     * reference may be off, which is the normal state inside a car.
     */
    val sensorAccuracy: Int,
    val speedMps: Double? = null,
    val courseDegrees: Double? = null,
    /** Which reference the heading camera is actually using right now. */
    val source: HeadingSource? = null,
)

/** Short label for [HeadingDebugSnapshot.sensorAccuracy]. */
fun sensorAccuracyLabel(status: Int): String = when (status) {
    3 -> "high"
    2 -> "medium"
    1 -> "low"
    0 -> "unreliable"
    else -> "no contact"
}

/**
 * Live heading numbers for the debug readout. Only debuggable builds publish into it, and the
 * readout renders only once something has been published, so release builds never show it.
 */
object HeadingDebug {
    private val _snapshot = MutableStateFlow<HeadingDebugSnapshot?>(null)
    val snapshot: StateFlow<HeadingDebugSnapshot?> = _snapshot.asStateFlow()

    fun publishHeading(breakdown: HeadingBreakdown, sensorAccuracy: Int) {
        _snapshot.update {
            it?.copy(breakdown = breakdown, sensorAccuracy = sensorAccuracy)
                ?: HeadingDebugSnapshot(breakdown, sensorAccuracy)
        }
    }

    /** No-op until a heading has been published, which is what keeps the readout out of release. */
    fun publishMotion(speedMps: Double?, courseDegrees: Double?, source: HeadingSource?) {
        _snapshot.update { it?.copy(speedMps = speedMps, courseDegrees = courseDegrees, source = source) }
    }
}
