package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

object TrackUtils {

    /** Tap target radius in map pixels; ~44dp finger diameter at typical densities. */
    const val TAP_RADIUS_PX = 22.0

    /**
     * Web Mercator ground resolution (meters per map pixel) at the given [latitude]
     * and MapLibre [zoom]. Shared so both platforms derive an identical tap tolerance.
     */
    fun metersPerPixel(latitude: Double, zoom: Double): Double =
        156543.03392 * cos(latitude * PI / 180.0) / 2.0.pow(zoom)

    /**
     * Returns [track] if [tap] falls within [maxDistanceMeters] of any of its
     * segments, else null. Callers should pass a zoom-scaled tolerance
     * (metersPerPixel * fingerRadiusPx) so the tap target is a consistent size.
     */
    fun findTappedTrack(
        tap: LatLng,
        track: Track?,
        maxDistanceMeters: Double = 80.0,
    ): Track? {
        if (track == null || track.points.size < 2) return null
        return if (minDistanceToTrackMeters(tap, track) <= maxDistanceMeters) track else null
    }

    /** Minimum distance from [tap] to the track polyline, in meters. */
    fun minDistanceToTrackMeters(tap: LatLng, track: Track): Double {
        // Local equirectangular projection centred on the tap (tap = origin).
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(tap.latitude * PI / 180.0)
        fun px(p: LatLng) = (p.longitude - tap.longitude) * mPerDegLon
        fun py(p: LatLng) = (p.latitude - tap.latitude) * mPerDegLat

        var best = Double.MAX_VALUE
        val pts = track.points
        for (i in 0 until pts.size - 1) {
            best = minOf(
                best,
                pointToSegment(
                    0.0, 0.0,
                    px(pts[i].latLng), py(pts[i].latLng),
                    px(pts[i + 1].latLng), py(pts[i + 1].latLng),
                ),
            )
        }
        return best
    }

    private fun pointToSegment(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double,
    ): Double {
        val abx = bx - ax; val aby = by - ay
        val apx = px - ax; val apy = py - ay
        val lenSq = abx * abx + aby * aby
        val t = if (lenSq == 0.0) 0.0 else ((apx * abx + apy * aby) / lenSq).coerceIn(0.0, 1.0)
        val cx = ax + t * abx; val cy = ay + t * aby
        val dx = px - cx; val dy = py - cy
        return sqrt(dx * dx + dy * dy)
    }
}
