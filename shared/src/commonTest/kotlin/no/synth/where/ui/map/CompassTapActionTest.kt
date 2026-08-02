package no.synth.where.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class CompassTapActionTest {

    private fun action(bearing: Double, northLocked: Boolean = false, following: Boolean = false) =
        compassTapAction(bearing, northLocked, following)

    @Test
    fun rotatedMapStraightensFirst() {
        assertEquals(CompassTapAction.RESET_NORTH, action(90.0))
        assertEquals(CompassTapAction.RESET_NORTH, action(1.0))
    }

    @Test
    fun secondTapLocksOnceFacingNorth() {
        assertEquals(CompassTapAction.TOGGLE_LOCK, action(0.0))
    }

    @Test
    fun nearNorthCountsAsNorth() {
        assertEquals(CompassTapAction.TOGGLE_LOCK, action(0.2))
        assertEquals(CompassTapAction.TOGGLE_LOCK, action(359.9))
        assertEquals(CompassTapAction.TOGGLE_LOCK, action(-0.1))
    }

    @Test
    fun lockedMapReleasesOnAnyTap() {
        assertEquals(CompassTapAction.TOGGLE_LOCK, action(0.0, northLocked = true))
        assertEquals(CompassTapAction.TOGGLE_LOCK, action(90.0, northLocked = true))
    }

    // Animating the bearing of a followed camera cancels the follow mode, so a rotated
    // heading-mode camera must lock in one step instead.
    @Test
    fun followingCameraLocksWithoutStraighteningFirst() {
        assertEquals(CompassTapAction.TOGGLE_LOCK, action(90.0, following = true))
    }
}
