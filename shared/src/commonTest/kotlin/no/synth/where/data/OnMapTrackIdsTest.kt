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
        // The same track in both sets is unreachable at runtime (navigation clears the viewing set),
        // but the function must still dedup it to a single id.
        val shared = track("a")
        val ids = onMapTrackIdsOf(listOf(shared, track("b")), NavigationSession(shared, reversed = true))
        assertEquals(setOf("a", "b"), ids)
    }
}
