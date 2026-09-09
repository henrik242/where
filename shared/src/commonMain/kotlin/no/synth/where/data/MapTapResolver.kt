package no.synth.where.data

import no.synth.where.data.geo.LatLng

/** What a map tap landed on, in the order the map gives them precedence. */
sealed interface MapTapTarget {
    data class Point(val point: SavedPoint) : MapTapTarget

    /** A live shared point: [mine] distinguishes one this client owns (editable) from a friend's. */
    data class SharedPoint(val point: no.synth.where.data.SharedPoint, val mine: Boolean) : MapTapTarget
    data class TrackLine(val trackId: String) : MapTapTarget

    /** Tapped away from any track while tracks were tappable, i.e. a tap that closes track focus. */
    data object OutsideTracks : MapTapTarget
    data object Nothing : MapTapTarget
}

private const val POINT_TAP_RADIUS_M = 500.0

/**
 * Resolves a map tap against saved points, live shared points (own and friends') and tappable
 * tracks. Shared so both platforms give points precedence over tracks and use the same tap radius;
 * among points the nearest within [POINT_TAP_RADIUS_M] wins.
 */
fun resolveMapTap(
    tap: LatLng,
    zoom: Double,
    savedPoints: List<SavedPoint>,
    viewingTracks: List<Track>,
    navigationTrack: Track?,
    mySharedPoints: List<SharedPoint> = emptyList(),
    friendSharedPoints: List<SharedPoint> = emptyList(),
): MapTapTarget {
    val candidates = buildList {
        SavedPointUtils.findNearestPoint(tap, savedPoints, POINT_TAP_RADIUS_M)?.let {
            add(tap.distanceTo(it.latLng) to MapTapTarget.Point(it))
        }
        nearestShared(tap, mySharedPoints)?.let {
            add(tap.distanceTo(it.latLng) to MapTapTarget.SharedPoint(it, mine = true))
        }
        nearestShared(tap, friendSharedPoints)?.let {
            add(tap.distanceTo(it.latLng) to MapTapTarget.SharedPoint(it, mine = false))
        }
    }
    candidates.minByOrNull { it.first }?.let { return it.second }

    val tracks = TrackUtils.tappableTracks(viewingTracks, navigationTrack)
    val tolerance = TrackUtils.metersPerPixel(tap.latitude, zoom) * TrackUtils.TAP_RADIUS_PX
    TrackUtils.findTappedTrack(tap, tracks, tolerance)?.let { return MapTapTarget.TrackLine(it.id) }

    return if (tracks.isNotEmpty()) MapTapTarget.OutsideTracks else MapTapTarget.Nothing
}

private fun nearestShared(tap: LatLng, points: List<SharedPoint>): SharedPoint? =
    points.minByOrNull { tap.distanceTo(it.latLng) }
        ?.takeIf { tap.distanceTo(it.latLng) < POINT_TAP_RADIUS_M }
