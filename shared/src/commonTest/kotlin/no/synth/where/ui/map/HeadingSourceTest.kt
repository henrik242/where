package no.synth.where.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HeadingSourceTest {

    private fun source(
        current: HeadingSource,
        speedMps: Double?,
        hasCourse: Boolean = true,
        sinceCourse: Duration? = 0.seconds,
    ) = headingSourceFor(current, speedMps, hasCourse, sinceCourse)

    @Test
    fun drivingSpeedsUseTheCourseOverGround() {
        assertEquals(HeadingSource.COURSE, source(HeadingSource.COMPASS, 2.5))
        assertEquals(HeadingSource.COURSE, source(HeadingSource.COMPASS, 22.0))
    }

    @Test
    fun stoppingAfterTravellingHoldsTheLastCourse() {
        // A red light must not swing the map onto a compass reading that is 60 degrees out.
        assertEquals(HeadingSource.HELD, source(HeadingSource.COURSE, 0.0))
        assertEquals(HeadingSource.HELD, source(HeadingSource.HELD, 0.0))
        assertEquals(HeadingSource.COURSE, source(HeadingSource.HELD, 20.0))
    }

    @Test
    fun theHoldExpiresSoWalkingAwayGetsTheCompassBack() {
        assertEquals(HeadingSource.HELD, source(HeadingSource.COURSE, 0.0, sinceCourse = 90.seconds))
        assertEquals(HeadingSource.COMPASS, source(HeadingSource.COURSE, 0.0, sinceCourse = 5.minutes))
        // Parked, got out, walking: the band would keep HELD, but the expiry overrides it.
        assertEquals(HeadingSource.COMPASS, source(HeadingSource.HELD, 1.5, sinceCourse = 5.minutes))
    }

    @Test
    fun standingStillWithoutHavingTravelledUsesTheCompass() {
        assertEquals(HeadingSource.COMPASS, source(HeadingSource.COMPASS, 0.0, sinceCourse = null))
        assertEquals(HeadingSource.COMPASS, source(HeadingSource.COMPASS, 1.0, sinceCourse = null))
    }

    @Test
    fun theBandBetweenThresholdsKeepsWhicheverIsInUse() {
        // A stop-and-go queue crosses this band constantly; swapping references there would swing
        // the map by however far the phone is turned away from the direction of travel.
        assertEquals(HeadingSource.COMPASS, source(HeadingSource.COMPASS, 1.5))
        assertEquals(HeadingSource.COURSE, source(HeadingSource.COURSE, 1.5))
        assertEquals(HeadingSource.HELD, source(HeadingSource.HELD, 1.5))
    }

    @Test
    fun walkingStaysOnTheCompass() {
        // Never reaches the upper threshold, so the initial compass reading is never given up.
        var current = HeadingSource.COMPASS
        listOf(0.4, 1.2, 1.6, 1.4, 0.9, 1.5).forEach {
            current = source(current, it, sinceCourse = null)
        }
        assertEquals(HeadingSource.COMPASS, current)
    }

    @Test
    fun aFixWithoutCourseCannotStartFollowingIt() {
        assertEquals(HeadingSource.COMPASS, source(HeadingSource.COMPASS, 20.0, hasCourse = false))
        // Already travelling and the course drops out mid-band: keep what we have rather than jump.
        assertEquals(HeadingSource.COURSE, source(HeadingSource.COURSE, 20.0, hasCourse = false))
    }

    @Test
    fun aFixWithoutSpeedFallsBackRatherThanGuessing() {
        assertEquals(HeadingSource.HELD, source(HeadingSource.COURSE, null))
        assertEquals(HeadingSource.COMPASS, source(HeadingSource.COURSE, null, sinceCourse = null))
    }
}
