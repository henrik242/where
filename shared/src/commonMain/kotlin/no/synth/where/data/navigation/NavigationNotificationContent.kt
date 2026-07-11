package no.synth.where.data.navigation

import no.synth.where.util.formatDistance
import no.synth.where.util.formatElevation

/**
 * What the persistent navigation notification should say, with numeric values pre-formatted.
 * Kept as data (not localized text) so the mapping is testable in commonTest and each platform
 * only interpolates its own localized template strings around the values.
 */
sealed interface NavigationNotificationContent {
    /** No location fix yet. */
    data object Locating : NavigationNotificationContent

    data object Arrived : NavigationNotificationContent

    /** Off the route; [distanceToRoute] is the formatted distance back to the nearest point on it. */
    data class OffRoute(val distanceToRoute: String) : NavigationNotificationContent

    /** On route; [remainingAscent] is null when the track carries no usable altitude. */
    data class EnRoute(
        val remainingDistance: String,
        val remainingAscent: String?,
    ) : NavigationNotificationContent
}

/** Arrival wins over off-route, mirroring the on-screen banner's priority. */
fun NavigationProgress?.toNotificationContent(): NavigationNotificationContent = when {
    this == null -> NavigationNotificationContent.Locating
    atEnd -> NavigationNotificationContent.Arrived
    !onCourse -> NavigationNotificationContent.OffRoute(offCourseMeters.formatDistance())
    else -> NavigationNotificationContent.EnRoute(
        remainingDistance = remainingMeters.formatDistance(),
        remainingAscent = remainingAscent?.formatElevation(),
    )
}
