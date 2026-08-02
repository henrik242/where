package no.synth.where.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraFollowModeTest {

    @Test
    fun cyclesOffFollowHeadingAndBack() {
        assertEquals(CameraFollowMode.FOLLOW, CameraFollowMode.OFF.next())
        assertEquals(CameraFollowMode.FOLLOW_HEADING, CameraFollowMode.FOLLOW.next())
        assertEquals(CameraFollowMode.OFF, CameraFollowMode.FOLLOW_HEADING.next())
    }

    @Test
    fun threeTapsReturnToStart() {
        var mode = CameraFollowMode.OFF
        repeat(3) { mode = mode.next() }
        assertEquals(CameraFollowMode.OFF, mode)
    }

    @Test
    fun northLockedCycleSkipsHeading() {
        assertEquals(CameraFollowMode.FOLLOW, CameraFollowMode.OFF.next(northLocked = true))
        assertEquals(CameraFollowMode.OFF, CameraFollowMode.FOLLOW.next(northLocked = true))
        assertEquals(CameraFollowMode.OFF, CameraFollowMode.FOLLOW_HEADING.next(northLocked = true))
    }

    @Test
    fun withoutHeadingDowngradesOnlyHeadingMode() {
        assertEquals(CameraFollowMode.FOLLOW, CameraFollowMode.FOLLOW_HEADING.withoutHeading())
        assertEquals(CameraFollowMode.FOLLOW, CameraFollowMode.FOLLOW.withoutHeading())
        assertEquals(CameraFollowMode.OFF, CameraFollowMode.OFF.withoutHeading())
    }
}
