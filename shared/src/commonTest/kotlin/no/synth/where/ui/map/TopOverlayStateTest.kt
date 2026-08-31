package no.synth.where.ui.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertFalse(s.hidesZoomControls)
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
        assertTrue(s.hidesZoomControls)
        assertTrue(s.hidesTopCenter)
    }

    @Test
    fun focusedTrack_hidesCornerControlsAndTopCenter() {
        val s = state(hasFocusedTrack = true)
        assertTrue(s.hidesCornerControls)
        assertTrue(s.hidesZoomControls)
        assertTrue(s.hidesTopCenter)
    }

    @Test
    fun viewingPoint_hidesCornerControlsAndTopCenter() {
        val s = state(hasViewingPoint = true)
        assertTrue(s.hidesCornerControls)
        assertTrue(s.hidesZoomControls)
        assertTrue(s.hidesTopCenter)
    }

    @Test
    fun following_keepsZoomControlsVisible_butHidesTheRest() {
        // The friend banner is up for a whole trip, so the zoom controls move below it instead.
        val s = state(isFollowing = true)
        assertTrue(s.hidesCornerControls)
        assertFalse(s.hidesZoomControls, "zoom controls drop below the friend banner")
        assertTrue(s.hidesTopCenter)
    }

    @Test
    fun searchWhileFollowing_hidesZoomControls() {
        // Search replaces the friend banner, so there is nothing to sit below.
        assertTrue(state(showSearch = true, isFollowing = true).hidesZoomControls)
    }

    private fun stack(
        isNavigating: Boolean = false,
        hasFocusedTrack: Boolean = false,
        showsPointBanner: Boolean = false,
        showsFriendBanner: Boolean = false,
        navCardHeight: Dp = 0.dp,
        trackBannerHeight: Dp = 0.dp,
        pointBannerHeight: Dp = 0.dp,
        friendBannerHeight: Dp = 0.dp,
    ) = topCenterStack(
        isNavigating = isNavigating,
        hasFocusedTrack = hasFocusedTrack,
        showsPointBanner = showsPointBanner,
        showsFriendBanner = showsFriendBanner,
        navCardHeight = navCardHeight,
        trackBannerHeight = trackBannerHeight,
        pointBannerHeight = pointBannerHeight,
        friendBannerHeight = friendBannerHeight,
    )

    @Test
    fun emptyStack_keepsCompassAtDefault() {
        val s = stack()
        assertEquals(16.dp, s.compassTopOffset, "no top overlay shown")
        assertEquals(16.dp, s.belowPrimaryModal)
        assertEquals(16.dp, s.pointBannerTop)
        assertEquals(16.dp, s.friendBannerTop)
    }

    @Test
    fun navigating_dropsCompassBelowNavCard() {
        val s = stack(isNavigating = true, navCardHeight = 60.dp)
        assertEquals(16.dp + 60.dp + 8.dp, s.compassTopOffset)
        assertEquals(16.dp + 60.dp + 8.dp, s.belowPrimaryModal, "chips share the row below the card")
    }

    @Test
    fun focusedTrack_dropsCompassBelowBanner() {
        val s = stack(hasFocusedTrack = true, trackBannerHeight = 48.dp)
        assertEquals(16.dp + 48.dp + 8.dp, s.compassTopOffset)
    }

    @Test
    fun navigatingWithFocusedTrack_navCardWins() {
        val s = stack(
            isNavigating = true, hasFocusedTrack = true,
            navCardHeight = 60.dp, trackBannerHeight = 48.dp,
        )
        assertEquals(16.dp + 60.dp + 8.dp, s.compassTopOffset, "stale banner height must be ignored")
    }

    @Test
    fun unmeasuredModal_keepsCompassAtDefault() {
        val s = stack(isNavigating = true)
        assertEquals(16.dp, s.compassTopOffset, "unmeasured modal must not offset the compass")
    }

    @Test
    fun pointBanner_stacksBelowNavCard_andCompassBelowBoth() {
        val s = stack(
            isNavigating = true, showsPointBanner = true,
            navCardHeight = 60.dp, pointBannerHeight = 40.dp,
        )
        val belowCard = 16.dp + 60.dp + 8.dp
        assertEquals(belowCard, s.pointBannerTop)
        assertEquals(belowCard + 40.dp + 8.dp, s.compassTopOffset, "point banner must not cover the compass")
    }

    @Test
    fun friendBanner_stacksBelowPointBanner_whileNavigating() {
        val s = stack(
            isNavigating = true, showsPointBanner = true, showsFriendBanner = true,
            navCardHeight = 60.dp, pointBannerHeight = 40.dp, friendBannerHeight = 56.dp,
        )
        val belowPoint = 16.dp + 60.dp + 8.dp + 40.dp + 8.dp
        assertEquals(belowPoint, s.friendBannerTop)
        assertEquals(belowPoint + 56.dp + 8.dp, s.compassTopOffset)
    }

    @Test
    fun friendBanner_stacksBelowFocusedTrackBanner() {
        val s = stack(
            hasFocusedTrack = true, showsFriendBanner = true,
            trackBannerHeight = 48.dp, friendBannerHeight = 56.dp,
        )
        assertEquals(16.dp + 48.dp + 8.dp, s.friendBannerTop, "derived from the measured banner, not hardcoded")
        assertEquals(16.dp + 48.dp + 8.dp + 56.dp + 8.dp, s.compassTopOffset)
    }

    @Test
    fun pointBannerAlone_movesCompassBelowIt() {
        val s = stack(showsPointBanner = true, pointBannerHeight = 40.dp)
        assertEquals(16.dp, s.pointBannerTop)
        assertEquals(16.dp + 40.dp + 8.dp, s.compassTopOffset)
    }
}
