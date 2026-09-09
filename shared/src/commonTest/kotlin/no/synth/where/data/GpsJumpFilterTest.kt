package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpsJumpFilterTest {

    // Roughly 111 m per 0.001 deg latitude near the equator/Oslo.
    private val base = LatLng(59.9139, 10.7522)
    private fun north(meters: Double) = LatLng(base.latitude + meters / 111_320.0, base.longitude)

    @Test
    fun firstPointAlwaysAccepted() {
        val f = GpsJumpFilter()
        assertTrue(f.accept(base, 0))
    }

    @Test
    fun plausibleWalkingSpeedAccepted() {
        val f = GpsJumpFilter()
        f.accept(base, 0)
        // 5 m in 2 s = 2.5 m/s
        assertTrue(f.accept(north(5.0), 2_000))
    }

    @Test
    fun teleportRejected() {
        val f = GpsJumpFilter()
        f.accept(base, 0)
        // 3 km in 1 s
        assertFalse(f.accept(north(3_000.0), 1_000))
    }

    @Test
    fun anchorUnchangedAfterRejectSoRecoveryIsMeasuredFromLastGood() {
        val f = GpsJumpFilter()
        f.accept(base, 0)
        assertFalse(f.accept(north(3_000.0), 1_000)) // teleport rejected
        // Next real fix 5 m from the anchor, 3 s after the anchor, is accepted.
        assertTrue(f.accept(north(5.0), 3_000))
    }

    @Test
    fun sameTimestampAccepted() {
        val f = GpsJumpFilter()
        f.accept(base, 5_000)
        // dt <= 0: speed is undefined, so the point is kept rather than dropped.
        assertTrue(f.accept(north(3_000.0), 5_000))
    }

    @Test
    fun longTimeGapMakesFarPointPlausible() {
        val f = GpsJumpFilter()
        f.accept(base, 0)
        // 3 km over an hour = ~0.8 m/s
        assertTrue(f.accept(north(3_000.0), 3_600_000))
    }

    @Test
    fun staleAnchorReanchorsAfterConsecutiveRejects() {
        val f = GpsJumpFilter(maxConsecutiveRejects = 3)
        f.accept(base, 0)
        val far = north(3_000.0)
        assertFalse(f.accept(far, 1_000))
        assertFalse(f.accept(far, 2_000))
        assertTrue(f.accept(far, 3_000)) // 3rd reject accepts and re-anchors
        // Now measured from the new anchor: a small move nearby is accepted.
        assertTrue(f.accept(north(3_005.0), 5_000))
    }
}
