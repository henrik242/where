package no.synth.where.data.geo

import kotlin.math.roundToInt

/**
 * Maps a [bearing] (degrees clockwise from north) to an 8-point compass label built from the
 * localized cardinal letters, e.g. N, NE, E, SE ... The letters are passed in so the math stays
 * in shared code while the caller localizes them (N/E/S/W in English, N/Ø/S/V in Norwegian).
 */
fun compassPoint8(bearing: Double, n: String, e: String, s: String, w: String): String {
    val points = arrayOf(n, n + e, e, s + e, s, s + w, w, n + w)
    val normalized = ((bearing % 360.0) + 360.0) % 360.0 // negative-safe; callers may pass any degree
    val idx = (normalized / 45.0).roundToInt() % 8       // %8 wraps the 337.5..360 bucket back to N
    return points[idx]
}

/** Whole degrees 0..359 for a [bearing] in any range, for a heading readout like "45°". */
fun wholeDegrees(bearing: Double): Int {
    val normalized = ((bearing % 360.0) + 360.0) % 360.0
    return normalized.roundToInt() % 360 // 359.6 rounds to 360, which reads as 0
}
