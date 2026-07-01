package no.synth.where.data.navigation

import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.projectOntoSegment
import kotlin.math.min

/**
 * Tracks a user's progress along a fixed track. Stateful: [progressAt] advances an internal
 * cursor forward along the route on each call, so call it repeatedly with the live location
 * during a single traversal, and build a fresh instance when the track or direction changes.
 * Cumulative distance and remaining ascent/descent are precomputed as suffix sums in `init`;
 * off-course detection uses hysteresis (enter above [offCourseEnterM], clear below [offCourseExitM]).
 */
class TrackNavigator(
    track: Track,
    val reversed: Boolean,
    private val offCourseEnterM: Double = 35.0,
    private val offCourseExitM: Double = 25.0,   // hysteresis; must be <= enter
    private val arrivalM: Double = 25.0,
    private val altDeadbandM: Double = 2.0,      // per-segment altitude noise floor
) {
    private val points: List<TrackPoint> =
        if (reversed) track.points.asReversed() else track.points

    private val n = points.size
    private val cumDist = DoubleArray(n)         // distance from start to vertex i
    private val ascTo = DoubleArray(n)           // ascent from vertex i to end
    private val descTo = DoubleArray(n)          // descent from vertex i to end
    private val hasAltitude: Boolean =
        points.count { it.altitude != null } >= 2

    private val total: Double

    private var cursor = 0
    private var wasOffCourse = false

    init {
        for (i in 1 until n) {
            cumDist[i] = cumDist[i - 1] + points[i - 1].latLng.distanceTo(points[i].latLng)
        }
        total = if (n > 0) cumDist[n - 1] else 0.0

        for (i in n - 2 downTo 0) {
            val a = points[i].altitude
            val b = points[i + 1].altitude
            var up = 0.0
            var down = 0.0
            if (a != null && b != null) {
                val delta = b - a
                if (delta > altDeadbandM) up = delta
                else if (-delta > altDeadbandM) down = -delta
            }
            ascTo[i] = ascTo[i + 1] + up
            descTo[i] = descTo[i + 1] + down
        }
    }

    fun progressAt(location: LatLng): NavigationProgress {
        if (n < 2) {
            val only = points.firstOrNull()?.latLng ?: location
            return NavigationProgress(
                onCourse = true, offCourseMeters = 0.0, snapped = only,
                remainingMeters = 0.0, remainingAscent = null, remainingDescent = null,
                atEnd = true
            )
        }

        val match = findClosestSegment(location)
        cursor = match.segment

        val segStart = points[match.segment]
        val segEnd = points[match.segment + 1]
        val segLen = segStart.latLng.distanceTo(segEnd.latLng)
        val distToSnapped = cumDist[match.segment] + match.t * segLen
        val remaining = (total - distToSnapped).coerceAtLeast(0.0)

        val (remAscent, remDescent) = remainingAltitude(match.segment, match.t)

        val onCourse =
            if (wasOffCourse) match.distanceMeters < offCourseExitM
            else match.distanceMeters <= offCourseEnterM
        wasOffCourse = !onCourse

        val atEnd = remaining < arrivalM ||
            location.distanceTo(points[n - 1].latLng) < arrivalM

        return NavigationProgress(
            onCourse = onCourse,
            offCourseMeters = match.distanceMeters,
            snapped = match.point,
            remainingMeters = remaining,
            remainingAscent = remAscent,
            remainingDescent = remDescent,
            atEnd = atEnd
        )
    }

    private data class SegmentMatch(
        val segment: Int,
        val t: Double,
        val point: LatLng,
        val distanceMeters: Double
    )

    /**
     * Closest segment to [location]. Searches a window forward of the cursor first, so
     * out-and-back / looped tracks don't snap to a later leg; falls back to a global search
     * only when the local best is poor (a large GPS jump / re-acquiring the route).
     */
    private fun findClosestSegment(location: LatLng): SegmentMatch {
        val windowed = bestSegmentIn(cursor..min(n - 2, cursor + WINDOW), location)
        val best = if (windowed.distanceMeters > REACQUIRE_M) {
            minOf(windowed, bestSegmentIn(0..n - 2, location), compareBy { it.distanceMeters })
        } else {
            windowed
        }
        return best
    }

    private fun bestSegmentIn(range: IntRange, location: LatLng): SegmentMatch {
        var best = SegmentMatch(range.first, 0.0, location, Double.MAX_VALUE)
        for (i in range) {
            val pr = projectOntoSegment(location, points[i].latLng, points[i + 1].latLng)
            if (pr.distanceMeters < best.distanceMeters) {
                best = SegmentMatch(i, pr.t, pr.point, pr.distanceMeters)
            }
        }
        return best
    }

    /** Remaining ascent/descent from the snapped position on [segment] (at fraction [t]) to the end. */
    private fun remainingAltitude(segment: Int, t: Double): Pair<Double?, Double?> {
        if (!hasAltitude) return null to null
        val altA = points[segment].altitude
        val altB = points[segment + 1].altitude
        if (altA == null || altB == null) {
            // altitude present overall but missing on this segment: fall back to suffix only
            return ascTo[segment + 1] to descTo[segment + 1]
        }
        val snappedAlt = altA + t * (altB - altA)
        val partialUp = (altB - snappedAlt).coerceAtLeast(0.0)
        val partialDown = (snappedAlt - altB).coerceAtLeast(0.0)
        return (ascTo[segment + 1] + partialUp) to (descTo[segment + 1] + partialDown)
    }

    // Exposed for the split-line renderer.
    fun currentSegment(): Int = cursor

    private companion object {
        const val WINDOW = 40
        const val REACQUIRE_M = 60.0
    }
}
