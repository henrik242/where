package no.synth.where.data.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

data class SegmentProjection(
    val point: LatLng,       // closest point on segment [a,b]
    val t: Double,           // clamped position along the segment, 0..1
    val distanceMeters: Double
)

/**
 * Closest point on segment a→b to p, using a local equirectangular projection
 * around `a`. Accurate at hiking segment scale (tens of metres).
 */
fun projectOntoSegment(p: LatLng, a: LatLng, b: LatLng): SegmentProjection {
    val mPerLat = 111_320.0
    val mPerLng = 111_320.0 * cos(a.latitude * PI / 180.0)

    val bx = (b.longitude - a.longitude) * mPerLng
    val by = (b.latitude - a.latitude) * mPerLat
    val px = (p.longitude - a.longitude) * mPerLng
    val py = (p.latitude - a.latitude) * mPerLat

    val segLen2 = bx * bx + by * by
    val t = if (segLen2 == 0.0) 0.0 else ((px * bx + py * by) / segLen2).coerceIn(0.0, 1.0)

    val projX = t * bx
    val projY = t * by
    val ddx = px - projX
    val ddy = py - projY
    val dist = sqrt(ddx * ddx + ddy * ddy)

    val projPoint = LatLng(
        latitude = a.latitude + projY / mPerLat,
        longitude = a.longitude + projX / mPerLng
    )
    return SegmentProjection(projPoint, t, dist)
}
