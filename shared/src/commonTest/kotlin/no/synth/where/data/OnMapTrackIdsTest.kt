package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OnMapTrackIdsTest {

    private fun track(id: String) = Track(id = id, name = id, points = emptyList(), startTime = 0L)

    @Test
    fun noTracksAndNoNavigationIsEmpty() {
        assertEquals(emptySet<String>(), onMapTrackIdsOf(emptyList(), null))
    }

    @Test
    fun viewingTracksAreOnMap() {
        val ids = onMapTrackIdsOf(listOf(track("a"), track("b")), null)
        assertEquals(setOf("a", "b"), ids)
    }

    @Test
    fun navigatedTrackIsOnMap() {
        val ids = onMapTrackIdsOf(emptyList(), NavigationSession(track("nav"), reversed = false))
        assertEquals(setOf("nav"), ids)
    }

    @Test
    fun viewingAndNavigationAreCombinedAndDeduplicated() {
        // Other tracks stay in the viewing set while navigating; the repository keeps the navigated
        // track out of it, but the function must still dedup should the same id appear in both.
        val shared = track("a")
        val ids = onMapTrackIdsOf(listOf(shared, track("b")), NavigationSession(shared, reversed = true))
        assertEquals(setOf("a", "b"), ids)
    }
}
