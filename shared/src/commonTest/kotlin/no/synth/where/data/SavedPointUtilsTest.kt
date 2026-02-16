package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedPointUtilsTest {

    private fun point(id: String, lat: Double, lng: Double) = SavedPoint(
        id = id, name = "Point $id", latLng = LatLng(lat, lng)
    )

    @Test
    fun emptyListReturnsNull() {
        val result = SavedPointUtils.findNearestPoint(
            tapLocation = LatLng(60.0, 10.0),
            savedPoints = emptyList()
        )
        assertNull(result)
    }

    @Test
    fun pointWithinRangeIsReturned() {
        val saved = point("1", 60.0, 10.0)
        val result = SavedPointUtils.findNearestPoint(
            tapLocation = LatLng(60.001, 10.001),
            savedPoints = listOf(saved)
        )
        assertEquals(saved, result)
    }

    @Test
    fun pointBeyondMaxDistanceReturnsNull() {
        val saved = point("1", 60.0, 10.0)
        val result = SavedPointUtils.findNearestPoint(
            tapLocation = LatLng(61.0, 11.0),
            savedPoints = listOf(saved)
        )
        assertNull(result)
    }

    @Test
    fun returnsClosestWhenMultiplePoints() {
        val far = point("1", 60.003, 10.003)
        val near = point("2", 60.001, 10.001)
        val result = SavedPointUtils.findNearestPoint(
            tapLocation = LatLng(60.0, 10.0),
            savedPoints = listOf(far, near)
        )
        assertEquals(near, result)
    }

    @Test
    fun customMaxDistanceIsRespected() {
        val saved = point("1", 60.001, 10.001)
        // ~157m away — within default 500m but outside 50m
        val result = SavedPointUtils.findNearestPoint(
            tapLocation = LatLng(60.0, 10.0),
            savedPoints = listOf(saved),
            maxDistanceMeters = 50.0
        )
        assertNull(result)
    }
}
