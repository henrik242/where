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
}
