package no.synth.where.data

import no.synth.where.data.geo.LatLng

/** What a map tap landed on, in the order the map gives them precedence. */
sealed interface MapTapTarget {
    data class Point(val point: SavedPoint) : MapTapTarget
    data class TrackLine(val trackId: String) : MapTapTarget

    /** Tapped away from any track while tracks were tappable, i.e. a tap that closes track focus. */
    data object OutsideTracks : MapTapTarget
    data object Nothing : MapTapTarget
}

/**
 * Resolves a map tap against the saved points and tappable tracks. Shared so both platforms give
 * saved points precedence over tracks and use the same tap radius.
 */
fun resolveMapTap(
    tap: LatLng,
    zoom: Double,
    savedPoints: List<SavedPoint>,
    viewingTracks: List<Track>,
    navigationTrack: Track?
): MapTapTarget {
    SavedPointUtils.findNearestPoint(tap, savedPoints)?.let { return MapTapTarget.Point(it) }

    val candidates = TrackUtils.tappableTracks(viewingTracks, navigationTrack)
    val tolerance = TrackUtils.metersPerPixel(tap.latitude, zoom) * TrackUtils.TAP_RADIUS_PX
    TrackUtils.findTappedTrack(tap, candidates, tolerance)?.let { return MapTapTarget.TrackLine(it.id) }

    return if (candidates.isNotEmpty()) MapTapTarget.OutsideTracks else MapTapTarget.Nothing
}
