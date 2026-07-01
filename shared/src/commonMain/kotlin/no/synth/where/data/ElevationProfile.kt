package no.synth.where.data

data class ElevationProfile(
    val distances: List<Double>,   // cumulative meters, aligned with elevations
    val elevations: List<Double>,
    val minEle: Double,
    val maxEle: Double,
    val totalDistance: Double,
    val gain: Double,
)

/** True if the track has enough altitude data to draw an elevation profile. */
fun Track.hasElevationData(): Boolean =
    points.size >= 2 && points.count { it.altitude != null } >= 2

/** Builds a downsampled elevation profile, or null if the track has fewer than 2 altitude points. */
fun Track.elevationProfileOrNull(maxSamples: Int = 240): ElevationProfile? {
    if (!hasElevationData()) return null

    val cum = ArrayList<Double>(points.size)
    val ele = ArrayList<Double>(points.size)
    var acc = 0.0
    var last = points.firstOrNull { it.altitude != null }?.altitude ?: 0.0
    for (i in points.indices) {
        if (i > 0) acc += points[i - 1].latLng.distanceTo(points[i].latLng)
        cum.add(acc)
        last = points[i].altitude ?: last     // carry-forward across gaps
        ele.add(last)
    }

    val step = if (cum.size > maxSamples) cum.size / maxSamples else 1
    val d = ArrayList<Double>()
    val e = ArrayList<Double>()
    var i = 0
    while (i < cum.size) {
        d.add(cum[i])
        e.add(ele[i])
        i += step
    }
    if (d.last() != cum.last()) {
        d.add(cum.last())
        e.add(ele.last())
    }

    var gain = 0.0
    for (k in 1 until e.size) {
        val diff = e[k] - e[k - 1]
        if (diff > 0) gain += diff
    }

    return ElevationProfile(d, e, e.min(), e.max(), acc, gain)
}
