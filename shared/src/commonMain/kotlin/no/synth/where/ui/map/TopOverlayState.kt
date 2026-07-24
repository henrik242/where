package no.synth.where.ui.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Inset above the first top-center overlay, and the gap between stacked overlays. */
internal val TOP_OVERLAY_INSET = 16.dp
internal val TOP_OVERLAY_GAP = 8.dp

/**
 * Vertical layout of the full-width top-center overlay stack, in dp from the map's top edge.
 * Occupants stack top-down with [TOP_OVERLAY_GAP] between them: the primary modal
 * (NavigationCard while navigating, else the track banner / crop header while a track is
 * focused), then the viewing-point banner, then the following-friend banner. The map's native
 * compass ornament (top-right) drops below the lowest occupant so none of them cover it.
 * Heights are measured, so 0 until first layout; a zero-height occupant pushes nothing down,
 * keeping the first frame stable.
 */
data class TopCenterStack(
    /** Top for content directly under the primary modal (the corner chips while navigating). */
    val belowPrimaryModal: Dp,
    val pointBannerTop: Dp,
    val friendBannerTop: Dp,
    /** Top margin for the native compass; 0 keeps it at its platform default position. */
    val compassTopOffset: Dp,
)

fun topCenterStack(
    isNavigating: Boolean,
    hasFocusedTrack: Boolean,
    showsPointBanner: Boolean,
    showsFriendBanner: Boolean,
    navCardHeight: Dp,
    trackBannerHeight: Dp,
    pointBannerHeight: Dp,
    friendBannerHeight: Dp,
): TopCenterStack {
    // Bottom edge of the stack so far; TOP_OVERLAY_INSET means nothing visible is placed yet.
    var bottom = TOP_OVERLAY_INSET
    fun place(height: Dp): Dp {
        val top = if (bottom > TOP_OVERLAY_INSET) bottom + TOP_OVERLAY_GAP else TOP_OVERLAY_INSET
        bottom = top + height
        return top
    }
    val primaryHeight = when {
        isNavigating -> navCardHeight
        hasFocusedTrack -> trackBannerHeight
        else -> null
    }
    if (primaryHeight != null) place(primaryHeight)
    val belowPrimaryModal =
        if (primaryHeight != null) bottom + TOP_OVERLAY_GAP else TOP_OVERLAY_INSET
    val pointBannerTop = if (showsPointBanner) place(pointBannerHeight) else TOP_OVERLAY_INSET
    val friendBannerTop = if (showsFriendBanner) place(friendBannerHeight) else TOP_OVERLAY_INSET
    val compassTopOffset = if (bottom > TOP_OVERLAY_INSET) bottom + TOP_OVERLAY_GAP else 0.dp
    return TopCenterStack(belowPrimaryModal, pointBannerTop, friendBannerTop, compassTopOffset)
}
