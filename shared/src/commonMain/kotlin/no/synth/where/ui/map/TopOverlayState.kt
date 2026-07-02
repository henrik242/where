package no.synth.where.ui.map

/**
 * Which top-of-map overlays are active, factored out of [MapOverlays] so the deliberate
 * navigation asymmetry can be unit-tested: navigation occupies the top-center slot (hiding the
 * LocatingPill) but leaves the top-corner controls (zoom on the left, chips on the right) visible,
 * because the NavigationCard is centered and does not collide with them.
 */
data class TopOverlayState(
    /** Zoom controls and corner chips must hide. */
    val hidesCornerControls: Boolean,
    /** The top-center slot is taken, so the LocatingPill must hide. */
    val hidesTopCenter: Boolean,
)

fun topOverlayState(
    showSearch: Boolean,
    hasFocusedTrack: Boolean,
    hasViewingPoint: Boolean,
    isFollowing: Boolean,
    isNavigating: Boolean,
): TopOverlayState {
    val hidesCornerControls = showSearch || hasFocusedTrack || hasViewingPoint || isFollowing
    return TopOverlayState(
        hidesCornerControls = hidesCornerControls,
        hidesTopCenter = hidesCornerControls || isNavigating,
    )
}
