package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.projectOntoSegment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

object TrackUtils {

    /** Tap target radius in logical map pixels (~44dp finger diameter). */
    const val TAP_RADIUS_PX = 22.0

    /**
     * Web Mercator ground resolution (meters per map pixel) at the given [latitude]
     * and MapLibre [zoom]. Shared so both platforms derive an identical tap tolerance.
     */
    fun metersPerPixel(latitude: Double, zoom: Double): Double =
        156543.03392 * cos(latitude * PI / 180.0) / 2.0.pow(zoom)

    /**
     * The track closest to [tap] among [tracks], or null if none is within [maxDistanceMeters].
     * Used when several tracks are drawn at once so a tap focuses the nearest line. Callers should
     * pass a zoom-scaled tolerance (metersPerPixel * fingerRadiusPx) so the tap target is a
     * consistent size.
     */
    fun findTappedTrack(
        tap: LatLng,
        tracks: List<Track>,
        maxDistanceMeters: Double,
    ): Track? =
        tracks.filter { it.points.size >= 2 }
            .map { it to minDistanceToTrackMeters(tap, it) }
            .filter { it.second <= maxDistanceMeters }
            .minByOrNull { it.second }
            ?.first

    /** Minimum distance from [tap] to the track polyline, in meters. */
    fun minDistanceToTrackMeters(tap: LatLng, track: Track): Double =
        track.points.asSequence()
            .zipWithNext { a, b -> projectOntoSegment(tap, a.latLng, b.latLng).distanceMeters }
            .minOrNull() ?: Double.MAX_VALUE

    /**
     * Tracks the user can tap on the map: the viewing set plus the navigated route (tappable to open
     * its altitude chart). Order-independent — [findTappedTrack] picks the nearest.
     */
    fun tappableTracks(viewing: List<Track>, navigationTrack: Track?): List<Track> =
        if (navigationTrack != null) viewing + navigationTrack else viewing
}
