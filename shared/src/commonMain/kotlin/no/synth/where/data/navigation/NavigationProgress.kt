package no.synth.where.data.navigation

import no.synth.where.data.geo.LatLng

/** Snapshot of progress along the route at a single location, produced by [TrackNavigator]. */
data class NavigationProgress(
    val onCourse: Boolean,
    val offCourseMeters: Double,    // distance from the location to the route, valid even when onCourse
    val snapped: LatLng,            // closest point on the route; used by the split-line renderer
    val remainingMeters: Double,
    val remainingAscent: Double?,   // null when the track carries no usable altitude
    val remainingDescent: Double?,
    val atEnd: Boolean
)
