package no.synth.where.data

import no.synth.where.data.geo.LatLng

/**
 * Drops GPS fixes implying an impossible speed since the last accepted point — teleport outliers a
 * bad fix produces even when its accuracy looks fine (platform code already filters poor accuracy).
 * Anchors on the whole location stream regardless of recording, so track and live share agree.
 * Self-heals a stale anchor: a large time gap makes a far point plausible, and
 * [maxConsecutiveRejects] rejects in a row force a re-anchor (guards against a bad first fix).
 */
class GpsJumpFilter(
    private val maxSpeedMetersPerSecond: Double = MAX_SPEED_MPS,
    private val maxConsecutiveRejects: Int = MAX_CONSECUTIVE_REJECTS,
) {
    private var anchor: LatLng? = null
    private var anchorTimestamp: Long = 0
    private var consecutiveRejects: Int = 0

    /** True if [latLng] at [timestamp] (epoch millis) should be kept; an accepted point becomes the anchor. */
    fun accept(latLng: LatLng, timestamp: Long): Boolean {
        val from = anchor
        val dtSeconds = (timestamp - anchorTimestamp) / 1000.0
        if (from == null || dtSeconds <= 0.0) return acceptPoint(latLng, timestamp)

        val speed = from.distanceTo(latLng) / dtSeconds
        if (speed <= maxSpeedMetersPerSecond) return acceptPoint(latLng, timestamp)

        consecutiveRejects++
        if (consecutiveRejects >= maxConsecutiveRejects) return acceptPoint(latLng, timestamp)
        return false
    }

    private fun acceptPoint(latLng: LatLng, timestamp: Long): Boolean {
        anchor = latLng
        anchorTimestamp = timestamp
        consecutiveRejects = 0
        return true
    }

    companion object {
        // ~200 km/h: comfortably above car/train travel, far below the thousands of m/s a
        // multi-kilometer jump between consecutive ~1 Hz fixes implies.
        const val MAX_SPEED_MPS = 55.0
        const val MAX_CONSECUTIVE_REJECTS = 5
    }
}
