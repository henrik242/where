package no.synth.where.ui.map

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Where a heading-follow camera takes its bearing from.
 *
 * [COMPASS] is the device's own orientation, which is the right answer when someone is pointing
 * the phone at something: on foot, standing still, reading the map. It is the wrong answer in a
 * vehicle, and not by a little -- measured 62 degrees out on a straight road with the phone lying
 * flat and its top edge pointing along the road, with the magnetometer reporting high accuracy.
 * A car's own magnetization adds a bias fixed in the car's frame, so the error is a function of
 * which way you are pointing and cannot be calibrated away from inside the app.
 *
 * [COURSE] is the fix's course over ground: the direction actually travelled, whatever the phone
 * is doing, but only meaningful while moving faster than GPS noise.
 *
 * [HELD] keeps the bearing already on screen, which is the course from just before stopping.
 * Coming to a halt is not a reason to swing the map onto a compass reading that may be wildly
 * wrong; a car at a red light still faces the way it was driving.
 */
enum class HeadingSource { COMPASS, COURSE, HELD }

/** At or above this, ~9 km/h, the course over ground takes over. */
private const val COURSE_MIN_SPEED_MPS = 2.5

/** At or below this, ~3.6 km/h, we are no longer travelling. */
private const val COMPASS_MAX_SPEED_MPS = 1.0

/**
 * How long a stop keeps pointing the way it was travelling. Long enough for traffic lights and
 * junctions; short enough that parking, getting out and walking off hands the map back to the
 * compass, which out of the car is the honest reference again.
 */
private val COURSE_HOLD = 2.minutes

/**
 * The source to use next, given the one in use now.
 *
 * Between the two speed thresholds the current source is kept: the references disagree, so a
 * walking pace or a stop-and-go queue must not be able to flap the map between them. Walking never
 * reaches the upper threshold, so someone who has not been travelling stays on the compass and a
 * stop leaves them there.
 *
 * [speedMps] and [hasCourse] come from the current fix. [sinceCourse] is how long ago [COURSE] was
 * last actually in use, and null if it never has been; past [COURSE_HOLD] a stop stops holding.
 */
fun headingSourceFor(
    current: HeadingSource,
    speedMps: Double?,
    hasCourse: Boolean,
    sinceCourse: Duration? = null,
): HeadingSource {
    val travelling = speedMps != null && speedMps >= COURSE_MIN_SPEED_MPS
    val stopped = speedMps == null || speedMps <= COMPASS_MAX_SPEED_MPS
    val next = when {
        travelling && hasCourse -> HeadingSource.COURSE
        // A stop holds the bearing it arrived with, unless the compass was the reference all along.
        stopped -> if (current == HeadingSource.COMPASS) HeadingSource.COMPASS else HeadingSource.HELD
        else -> current
    }
    val holdExpired = sinceCourse == null || sinceCourse > COURSE_HOLD
    return if (next == HeadingSource.HELD && holdExpired) HeadingSource.COMPASS else next
}
