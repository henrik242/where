package no.synth.where.data

/** Per-segment altitude noise floor, in meters. Shared by the chart gain and navigation ascent. */
const val ALTITUDE_DEADBAND_M = 2.0

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

/**
 * Walks [elevations] (nulls skipped) accumulating ascent and descent against a moving reference,
 * confirming a change only once it exceeds [deadbandM]. Thresholding the running total rather than
 * each adjacent pair makes the result independent of point spacing, so a densely-sampled climb where
 * every step is under the dead-band still reports its true gain. [onStep] receives the running
 * (ascent, descent) totals after each index so callers can build a single total or cumulative arrays.
 */
inline fun accumulateAltitude(
    elevations: List<Double?>,
    deadbandM: Double = ALTITUDE_DEADBAND_M,
    onStep: (index: Int, ascent: Double, descent: Double) -> Unit = { _, _, _ -> },
): Pair<Double, Double> {
    var asc = 0.0
    var desc = 0.0
    var ref: Double? = null
    for (i in elevations.indices) {
        val alt = elevations[i]
        val r = ref
        if (alt != null && r != null) {
            val delta = alt - r
            if (delta > deadbandM) { asc += delta; ref = alt }
            else if (-delta > deadbandM) { desc += -delta; ref = alt }
        } else if (alt != null) {
            ref = alt
        }
        onStep(i, asc, desc)
    }
    return asc to desc
}

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

    // Range and gain come from the full-resolution series; only the drawn line is downsampled,
    // so a peak between drawn samples still shows up in the min/max/gain labels.
    val (gain, _) = accumulateAltitude(ele)

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

    return ElevationProfile(d, e, ele.min(), ele.max(), acc, gain)
}
