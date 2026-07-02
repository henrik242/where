package no.synth.where.ui.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopOverlayStateTest {

    private fun state(
        showSearch: Boolean = false,
        hasFocusedTrack: Boolean = false,
        hasViewingPoint: Boolean = false,
        isFollowing: Boolean = false,
        isNavigating: Boolean = false,
    ) = topOverlayState(
        showSearch = showSearch,
        hasFocusedTrack = hasFocusedTrack,
        hasViewingPoint = hasViewingPoint,
        isFollowing = isFollowing,
        isNavigating = isNavigating,
    )

    @Test
    fun nothingActive_showsEverything() {
        val s = state()
        assertFalse(s.hidesCornerControls)
        assertFalse(s.hidesTopCenter)
    }

    @Test
    fun navigation_keepsCornerControlsVisible_butHidesTopCenter() {
        // The core of the bug fix: navigation must NOT hide the corner controls, but it does
        // occupy the top-center slot (NavigationCard), so the LocatingPill hides.
        val s = state(isNavigating = true)
        assertFalse(s.hidesCornerControls, "corner controls stay visible while navigating")
        assertTrue(s.hidesTopCenter, "NavigationCard occupies the top-center slot")
    }

    @Test
    fun search_hidesCornerControlsAndTopCenter() {
        val s = state(showSearch = true)
        assertTrue(s.hidesCornerControls)
        assertTrue(s.hidesTopCenter)
    }

    @Test
    fun focusedTrack_hidesCornerControlsAndTopCenter() {
        val s = state(hasFocusedTrack = true)
        assertTrue(s.hidesCornerControls)
        assertTrue(s.hidesTopCenter)
    }

    @Test
    fun viewingPoint_hidesCornerControlsAndTopCenter() {
        val s = state(hasViewingPoint = true)
        assertTrue(s.hidesCornerControls)
        assertTrue(s.hidesTopCenter)
    }

    @Test
    fun following_hidesCornerControlsAndTopCenter() {
        val s = state(isFollowing = true)
        assertTrue(s.hidesCornerControls)
        assertTrue(s.hidesTopCenter)
    }
}
