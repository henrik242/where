package no.synth.where.data

/**
 * Coerce a raw (start, end) crop selection into a valid range for a track with [pointCount] points:
 * both indices in bounds and `start < end`, so the cropped track always keeps at least two points.
 * Single source of truth for the crop invariant, shared by the chart handles, the repository, and
 * the map rendering split.
 */
fun clampCropRange(pointCount: Int, start: Int, end: Int): Pair<Int, Int> {
    val last = pointCount - 1
    val s = start.coerceIn(0, last - 1)
    val e = end.coerceIn(s + 1, last)
    return s to e
}

/** Cumulative distance in meters at each point index. First element is 0.0 (empty if no points). */
fun Track.cumulativeDistances(): List<Double> {
    val out = ArrayList<Double>(points.size)
    var acc = 0.0
    for (i in points.indices) {
        if (i > 0) acc += points[i - 1].latLng.distanceTo(points[i].latLng)
        out.add(acc)
    }
    return out
}

/**
 * A new [Track] keeping only `points[startIndex..endIndex]` (inclusive), with [Track.startTime] and
 * [Track.endTime] recomputed from the kept points. The id and name are preserved (overwrite in
 * place). [startIndex]/[endIndex] are coerced into range and to keep at least two points, so callers
 * can pass raw handle values without pre-validating.
 */
fun Track.cropped(startIndex: Int, endIndex: Int): Track {
    if (points.size < 2) return this
    val (start, end) = clampCropRange(points.size, startIndex, endIndex)
    if (start == 0 && end == points.lastIndex) return this
    val kept = points.subList(start, end + 1).toList()
    return copy(
        points = kept,
        startTime = kept.first().timestamp,
        endTime = kept.last().timestamp,
    )
}
