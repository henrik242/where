package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackFocusBoundsTest {

    private fun trackOf(id: String, vararg latLngs: Pair<Double, Double>): Track = Track(
        id = id,
        name = id,
        points = latLngs.mapIndexed { i, (lat, lng) ->
            TrackPoint(latLng = LatLng(lat, lng), timestamp = 1000L + i)
        },
        startTime = 1000L,
        endTime = 1000L + latLngs.size,
    )

    // A track near Oslo and one near Trondheim, far enough apart that focusing one is
    // visibly different from the union spanning both.
    private val oslo = trackOf("oslo", 59.9 to 10.7, 60.0 to 10.8)
    private val trondheim = trackOf("trondheim", 63.4 to 10.3, 63.5 to 10.4)

    @Test
    fun focusedTrackInSetFitsThatTrackAloneNotTheUnion() {
        val bounds = Track.focusOrCombinedBounds(listOf(oslo, trondheim), focusedId = "trondheim")
        assertNotNull(bounds)
        // Regression guard for the midpoint-zoom bug: must equal the focused track's own
        // envelope and must NOT be the union of both tracks.
        assertEquals(trondheim.bounds(), bounds)
        assertTrue(bounds != Track.combinedBounds(listOf(oslo, trondheim)))
    }

    @Test
    fun focusIdNotInSetFallsBackToUnion() {
        val bounds = Track.focusOrCombinedBounds(listOf(oslo, trondheim), focusedId = "missing")
        assertEquals(Track.combinedBounds(listOf(oslo, trondheim)), bounds)
    }

    @Test
    fun nullFocusFitsTheUnion() {
        val bounds = Track.focusOrCombinedBounds(listOf(oslo, trondheim), focusedId = null)
        assertEquals(Track.combinedBounds(listOf(oslo, trondheim)), bounds)
    }

    @Test
    fun emptySetIsNullRegardlessOfFocus() {
        assertNull(Track.focusOrCombinedBounds(emptyList(), focusedId = null))
        assertNull(Track.focusOrCombinedBounds(emptyList(), focusedId = "oslo"))
    }

    @Test
    fun focusedTrackWithoutBoundsFallsBackToUnion() {
        val empty = trackOf("empty")
        val bounds = Track.focusOrCombinedBounds(listOf(empty, oslo), focusedId = "empty")
        assertEquals(oslo.bounds(), bounds)
    }

    @Test
    fun singlePointFocusedTrackCollapsesToThatPoint() {
        val single = trackOf("single", 60.0 to 10.0)
        val bounds = Track.focusOrCombinedBounds(listOf(single, trondheim), focusedId = "single")
        assertNotNull(bounds)
        assertTrue(bounds.isPoint)
        assertEquals(60.0, bounds.south)
        assertEquals(10.0, bounds.west)
    }
}
