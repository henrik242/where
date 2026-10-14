package no.synth.where.ui.map

import no.synth.where.data.geo.LatLng
import kotlin.math.abs
import kotlin.math.exp

/** Camera center and bearing (degrees), the part of the camera a follow mode moves. */
data class FollowPose(val center: LatLng, val bearing: Double)

/**
 * Moves a following camera a little each frame: the center glides to each new fix over the time
 * that fix took to arrive, and the bearing eases toward its target along the shorter arc.
 */
class FollowCameraAnimator {
    private var from: LatLng? = null
    private var to: LatLng? = null
    private var segmentStart = 0L
    private var segmentNanos = DEFAULT_SEGMENT_NANOS
    private var lastFrame = 0L

    /** The pose for frame [frameNanos], or null when [current] is already there. Null [targetBearing] keeps the bearing. */
    fun step(frameNanos: Long, current: FollowPose, target: LatLng, targetBearing: Double?): FollowPose? {
        if (target != to) {
            val previous = to
            segmentNanos = if (previous == null) DEFAULT_SEGMENT_NANOS else (frameNanos - segmentStart).coerceIn(MIN_SEGMENT_NANOS, MAX_SEGMENT_NANOS)
            // A first fix or a long way off (engaging follow from elsewhere): go straight there.
            from = if (previous == null || current.center.distanceTo(target) > SNAP_METERS) target else current.center
            to = target
            segmentStart = frameNanos
        }
        val dtSeconds = if (lastFrame == 0L) 1.0 / 60 else (frameNanos - lastFrame) / 1e9
        lastFrame = frameNanos

        val t = ((frameNanos - segmentStart).toDouble() / segmentNanos).coerceIn(0.0, 1.0)
        val a = from ?: target
        val center = if (t >= 1.0) target else LatLng(
            a.latitude + (target.latitude - a.latitude) * t,
            a.longitude + (target.longitude - a.longitude) * t,
        )

        var bearing = current.bearing
        if (targetBearing != null) {
            val delta = shortestDelta(current.bearing, targetBearing)
            bearing = if (abs(delta) < BEARING_DONE_DEGREES) targetBearing else current.bearing + delta * (1 - exp(-dtSeconds / BEARING_TAU_SECONDS))
        }
        bearing = ((bearing % 360.0) + 360.0) % 360.0

        // Bearing steps near the target are tiny but real; only an unchanged bearing counts as settled.
        val settled = current.center.distanceTo(center) < CENTER_DONE_METERS && abs(shortestDelta(current.bearing, bearing)) < BEARING_UNCHANGED_DEGREES
        return if (settled) null else FollowPose(center, bearing)
    }

    private companion object {
        const val DEFAULT_SEGMENT_NANOS = 1_000_000_000L
        const val MIN_SEGMENT_NANOS = 200_000_000L
        const val MAX_SEGMENT_NANOS = 2_000_000_000L
        const val SNAP_METERS = 2000.0
        /** About 95% of the way to a new compass reading in 0.6 s: smooth, without trailing far behind. */
        const val BEARING_TAU_SECONDS = 0.2
        const val BEARING_DONE_DEGREES = 0.05
        const val BEARING_UNCHANGED_DEGREES = 1e-4
        const val CENTER_DONE_METERS = 0.01

        /** Signed degrees from [from] to [to], in (-180, 180]. */
        fun shortestDelta(from: Double, to: Double): Double {
            val d = ((to - from) % 360.0 + 540.0) % 360.0 - 180.0
            return if (d == -180.0) 180.0 else d
        }
    }
}
