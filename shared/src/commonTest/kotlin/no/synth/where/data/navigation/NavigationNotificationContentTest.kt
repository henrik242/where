package no.synth.where.data.navigation

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationNotificationContentTest {

    private fun progress(
        onCourse: Boolean = true,
        offCourseMeters: Double = 0.0,
        remainingMeters: Double = 1234.0,
        remainingAscent: Double? = 56.4,
        remainingDescent: Double? = 12.0,
        atEnd: Boolean = false,
    ) = NavigationProgress(
        onCourse = onCourse,
        offCourseMeters = offCourseMeters,
        snapped = LatLng(60.0, 10.0),
        segment = 0,
        location = LatLng(60.0, 10.0),
        remainingMeters = remainingMeters,
        remainingAscent = remainingAscent,
        remainingDescent = remainingDescent,
        atEnd = atEnd,
    )

    @Test
    fun nullProgressIsLocating() {
        assertEquals(NavigationNotificationContent.Locating, null.toNotificationContent())
    }

    @Test
    fun enRouteFormatsDistanceAndAscent() {
        assertEquals(
            NavigationNotificationContent.EnRoute(remainingDistance = "1.23 km", remainingAscent = "56 m"),
            progress().toNotificationContent()
        )
    }

    @Test
    fun enRouteWithoutAltitudeDropsAscent() {
        assertEquals(
            NavigationNotificationContent.EnRoute(remainingDistance = "1.23 km", remainingAscent = null),
            progress(remainingAscent = null, remainingDescent = null).toNotificationContent()
        )
    }

    @Test
    fun offCourseReportsDistanceBackToTrack() {
        assertEquals(
            NavigationNotificationContent.OffRoute(distanceToRoute = "78 m"),
            progress(onCourse = false, offCourseMeters = 78.2).toNotificationContent()
        )
    }

    @Test
    fun arrivedWinsOverEverythingElse() {
        assertEquals(
            NavigationNotificationContent.Arrived,
            progress(atEnd = true, remainingMeters = 10.0).toNotificationContent()
        )
    }
}
