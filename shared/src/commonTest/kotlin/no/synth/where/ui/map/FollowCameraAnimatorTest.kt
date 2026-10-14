package no.synth.where.ui.map

import no.synth.where.data.geo.LatLng
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FollowCameraAnimatorTest {
    private val frame = 16_666_667L
    private val a = LatLng(60.0, 10.0)
    private val b = LatLng(60.0001, 10.0) // ~11 m north

    /** Runs frames from [startNanos] for [count] frames, feeding each pose back as the camera. */
    private fun run(anim: FollowCameraAnimator, start: FollowPose, startNanos: Long, count: Int, target: LatLng, bearing: Double?): Pair<FollowPose, Long> {
        var pose = start
        var t = startNanos
        repeat(count) {
            t += frame
            anim.step(t, pose, target, bearing)?.let { pose = it }
        }
        return pose to t
    }

    @Test
    fun firstFixJumps() {
        val anim = FollowCameraAnimator()
        val pose = assertNotNull(anim.step(frame, FollowPose(LatLng(59.0, 9.0), 0.0), a, null))
        assertEquals(a, pose.center)
    }

    @Test
    fun glidesToNextFixOverTheFixInterval() {
        val anim = FollowCameraAnimator()
        var (pose, t) = run(anim, FollowPose(a, 0.0), 0, 60, a, null) // settle on a for 1 s
        // Halfway through the next second the camera is about halfway to b.
        val half = run(anim, pose, t, 30, b, null)
        val progress = (half.first.center.latitude - a.latitude) / (b.latitude - a.latitude)
        assertTrue(progress in 0.4..0.6, "progress=$progress")
        val end = run(anim, half.first, half.second, 40, b, null).first
        assertEquals(b, end.center)
    }

    @Test
    fun farTargetSnaps() {
        val anim = FollowCameraAnimator()
        val (pose, t) = run(anim, FollowPose(a, 0.0), 0, 10, a, null)
        val far = LatLng(60.1, 10.0) // ~11 km
        assertEquals(far, assertNotNull(anim.step(t + frame, pose, far, null)).center)
    }

    @Test
    fun bearingTakesTheShortArc() {
        val anim = FollowCameraAnimator()
        val pose = assertNotNull(anim.step(frame, FollowPose(a, 350.0), a, 10.0))
        val moved = pose.bearing
        // Moving clockwise through north, never back through 180.
        assertTrue(moved > 350.0 || moved < 10.0, "bearing=$moved")
        val end = run(anim, pose, frame, 120, a, 10.0).first
        assertTrue(abs(end.bearing - 10.0) < 0.1, "bearing=${end.bearing}")
    }

    @Test
    fun nullBearingHolds() {
        val anim = FollowCameraAnimator()
        val end = run(anim, FollowPose(a, 123.0), 0, 30, a, null).first
        assertEquals(123.0, end.bearing)
    }

    @Test
    fun settledCameraIsLeftAlone() {
        val anim = FollowCameraAnimator()
        val (pose, t) = run(anim, FollowPose(a, 45.0), 0, 200, a, 45.0)
        assertNull(anim.step(t + frame, pose, a, 45.0))
    }
}
