package no.synth.where.data

import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulerStateTest {

    @Test
    fun initialStateIsInactiveAndEmpty() {
        val state = RulerState()
        assertFalse(state.isActive)
        assertTrue(state.points.isEmpty())
        assertEquals(0.0, state.getTotalDistanceMeters())
    }

    @Test
    fun addPointAppendsToList() {
        val state = RulerState()
            .addPoint(LatLng(60.0, 10.0))
            .addPoint(LatLng(61.0, 11.0))
        assertEquals(2, state.points.size)
        assertEquals(60.0, state.points[0].latLng.latitude)
        assertEquals(61.0, state.points[1].latLng.latitude)
    }

    @Test
    fun removeLastPointDropsLast() {
        val state = RulerState()
            .addPoint(LatLng(60.0, 10.0))
            .addPoint(LatLng(61.0, 11.0))
            .removeLastPoint()
        assertEquals(1, state.points.size)
        assertEquals(60.0, state.points[0].latLng.latitude)
    }

    @Test
    fun removeLastPointOnEmptyIsNoOp() {
        val state = RulerState().removeLastPoint()
        assertTrue(state.points.isEmpty())
    }

    @Test
    fun clearResetsPointsAndDeactivates() {
        val state = RulerState(isActive = true)
            .addPoint(LatLng(60.0, 10.0))
            .clear()
        assertTrue(state.points.isEmpty())
        assertFalse(state.isActive)
    }

    @Test
    fun getTotalDistanceMetersWithSinglePointReturnsZero() {
        val state = RulerState().addPoint(LatLng(60.0, 10.0))
        assertEquals(0.0, state.getTotalDistanceMeters())
    }

    @Test
    fun getTotalDistanceMetersIsPositiveForMultiplePoints() {
        val state = RulerState()
            .addPoint(LatLng(60.0, 10.0))
            .addPoint(LatLng(60.001, 10.001))
        assertTrue(state.getTotalDistanceMeters() > 0.0)
    }

    @Test
    fun getSegmentDistancesReturnsCorrectCount() {
        val state = RulerState()
            .addPoint(LatLng(60.0, 10.0))
            .addPoint(LatLng(60.001, 10.001))
            .addPoint(LatLng(60.002, 10.002))
        val segments = state.getSegmentDistances()
        assertEquals(2, segments.size)
        assertTrue(segments.all { it > 0.0 })
    }

    @Test
    fun totalDistanceEqualsSumOfSegments() {
        val state = RulerState()
            .addPoint(LatLng(60.0, 10.0))
            .addPoint(LatLng(60.01, 10.01))
            .addPoint(LatLng(60.02, 10.0))
        val total = state.getTotalDistanceMeters()
        val segmentSum = state.getSegmentDistances().sum()
        assertEquals(total, segmentSum, 0.001)
    }

    @Test
    fun addPointPreservesActiveState() {
        val active = RulerState(isActive = true).addPoint(LatLng(60.0, 10.0))
        assertTrue(active.isActive)
        val inactive = RulerState(isActive = false).addPoint(LatLng(60.0, 10.0))
        assertFalse(inactive.isActive)
    }
}
