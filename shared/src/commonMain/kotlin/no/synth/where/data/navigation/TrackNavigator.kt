package no.synth.where.data.navigation

import no.synth.where.data.ALTITUDE_DEADBAND_M
import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import no.synth.where.data.accumulateAltitude
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
    private val offCourseExitM: Double = 25.0,   // hysteresis; the off-course clear threshold
    private val arrivalM: Double = 25.0,
    private val altDeadbandM: Double = ALTITUDE_DEADBAND_M,
) {
    init {
        require(offCourseExitM <= offCourseEnterM) {
            "offCourseExitM ($offCourseExitM) must be <= offCourseEnterM ($offCourseEnterM) for hysteresis"
        }
    }

    private val points: List<TrackPoint> =
        if (reversed) track.points.asReversed() else track.points

    private val n = points.size
    private val cumDist = DoubleArray(n)         // distance from start to vertex i
    private val ascUpTo = DoubleArray(n)         // cumulative ascent from start to vertex i
    private val descUpTo = DoubleArray(n)        // cumulative descent from start to vertex i
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

        accumulateAltitude(points.map { it.altitude }, altDeadbandM) { i, asc, desc ->
            ascUpTo[i] = asc
            descUpTo[i] = desc
        }
    }

    fun progressAt(location: LatLng): NavigationProgress {
        if (n < 2) {
            val only = points.firstOrNull()?.latLng ?: location
            return NavigationProgress(
                onCourse = true, offCourseMeters = 0.0, snapped = only,
                segment = 0, location = location,
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

        // Arrival requires being both near the route (on course) and at the end of it. Small
        // "remaining" alone is not enough: standing far off-track next to the finish point (or,
        // after reversing, next to what is now the end) would otherwise read as arrived.
        val atEnd = onCourse && remaining < arrivalM

        return NavigationProgress(
            onCourse = onCourse,
            offCourseMeters = match.distanceMeters,
            snapped = match.point,
            segment = match.segment,
            location = location,
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
     * Closest segment to [location]. Searches a window around the cursor first — forward for the
     * usual case, and a short distance behind so a brief backtrack stays on course — so
     * out-and-back / looped tracks don't snap to a later leg; falls back to a global search only
     * when the local best is poor (a large GPS jump / re-acquiring the route).
     */
    private fun findClosestSegment(location: LatLng): SegmentMatch {
        val windowed = bestSegmentIn(backtrackStart()..min(n - 2, cursor + WINDOW), location)
        return if (windowed.distanceMeters > REACQUIRE_M) {
            minOf(windowed, bestSegmentIn(0..n - 2, location), compareBy { it.distanceMeters })
        } else {
            windowed
        }
    }

    /**
     * Lowest segment index within [REACQUIRE_M] behind the cursor. Distance-based (not a fixed
     * segment count) so the look-behind covers the same sub-reacquire zone whether the track is
     * coarsely or densely sampled, letting a short backtrack re-match without reading off-course.
     */
    private fun backtrackStart(): Int {
        var start = cursor
        while (start > 0 && cumDist[cursor] - cumDist[start - 1] <= REACQUIRE_M) start--
        return start
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
        val totalAsc = ascUpTo[n - 1]
        val totalDesc = descUpTo[n - 1]
        val segUp = ascUpTo[segment + 1] - ascUpTo[segment]
        val segDown = descUpTo[segment + 1] - descUpTo[segment]
        val remAsc = (totalAsc - ascUpTo[segment] - segUp * t).coerceAtLeast(0.0)
        val remDesc = (totalDesc - descUpTo[segment] - segDown * t).coerceAtLeast(0.0)
        return remAsc to remDesc
    }

    private companion object {
        const val WINDOW = 40          // segments ahead of the cursor to search before reacquiring
        const val REACQUIRE_M = 60.0   // if the windowed best is worse than this, search the whole route
    }
}
