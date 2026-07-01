package no.synth.where.ui.map

import no.synth.where.data.RulerPoint
import no.synth.where.data.SavedPoint
import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import no.synth.where.data.geo.LatLng
import no.synth.where.data.navigation.NavigationProgress
import no.synth.where.util.formatDistance
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapGeoJsonTest {

    private fun track3() = Track(
        name = "t",
        startTime = 0L,
        points = listOf(
            TrackPoint(LatLng(60.0, 10.0), timestamp = 0),
            TrackPoint(LatLng(60.0, 10.1), timestamp = 1),
            TrackPoint(LatLng(60.0, 10.2), timestamp = 2)
        )
    )

    private fun progress(
        onCourse: Boolean,
        snapped: LatLng,
        segment: Int = 0,
        atEnd: Boolean = false,
        location: LatLng = LatLng(60.0, 10.0),
    ) = NavigationProgress(
        onCourse = onCourse, offCourseMeters = if (onCourse) 5.0 else 200.0,
        snapped = snapped, segment = segment, location = location, remainingMeters = 100.0,
        remainingAscent = null, remainingDescent = null, atEnd = atEnd
    )

    @Test
    fun navigationLayersSplitWhenOnCourse() {
        val layers = buildNavigationLayers(
            track3(), reversed = false,
            progress = progress(onCourse = true, snapped = LatLng(60.0, 10.05), segment = 0)
        )
        assertContains(layers.completed, "[10.05,60.0]")   // completed ends at the snap
        assertContains(layers.remaining, "[10.05,60.0]")   // remaining starts at the snap
        assertContains(layers.remaining, "[10.2,60.0]")    // ...through the end
    }

    @Test
    fun navigationLayersSplitReversed() {
        // Reversed: the route is walked end-to-start, so completed starts at the last vertex.
        val layers = buildNavigationLayers(
            track3(), reversed = true,
            progress = progress(onCourse = true, snapped = LatLng(60.0, 10.15), segment = 0)
        )
        assertContains(layers.completed, "[10.2,60.0]")    // reversed start = original last vertex
        assertContains(layers.remaining, "[10.0,60.0]")    // ...through the original first vertex
    }

    @Test
    fun navigationLayersShowWholeRouteWhenOffCourse() {
        // Off course: completed is empty and the entire route is the remaining line to follow.
        val layers = buildNavigationLayers(
            track3(), reversed = false,
            progress = progress(
                onCourse = false, snapped = LatLng(60.0, 10.2), segment = 2,
                location = LatLng(60.5, 10.0)
            )
        )
        assertEquals("""{"type":"Feature","geometry":{"type":"LineString","coordinates":[]}}""", layers.completed)
        assertContains(layers.remaining, "[10.0,60.0]")
        assertContains(layers.remaining, "[10.2,60.0]")
        assertTrue(layers.offCourse != null)
    }

    @Test
    fun navigationLayersNoConnectorWhenArrivedOffCourse() {
        // Arrived overrides off course: no dashed connector even when onCourse is false.
        val layers = buildNavigationLayers(
            track3(), reversed = false,
            progress = progress(onCourse = false, snapped = LatLng(60.0, 10.2), segment = 2, atEnd = true)
        )
        assertEquals(null, layers.offCourse)
    }

    @Test
    fun buildTrackGeoJsonProducesLineString() {
        val points = listOf(
            TrackPoint(LatLng(60.0, 10.0), timestamp = 0),
            TrackPoint(LatLng(61.0, 11.0), timestamp = 1)
        )
        val json = buildTrackGeoJson(points)
        assertContains(json, "\"type\":\"LineString\"")
        assertContains(json, "[10.0,60.0]")
        assertContains(json, "[11.0,61.0]")
    }

    @Test
    fun buildSavedPointsGeoJsonProducesFeatureCollection() {
        val points = listOf(
            SavedPoint(id = "1", name = "Home", latLng = LatLng(60.0, 10.0), color = "#FF0000"),
            SavedPoint(id = "2", name = "Work", latLng = LatLng(61.0, 11.0))
        )
        val json = buildSavedPointsGeoJson(points)
        assertContains(json, "\"type\":\"FeatureCollection\"")
        assertContains(json, "\"name\":\"Home\"")
        assertContains(json, "\"color\":\"#FF0000\"")
        assertContains(json, "\"name\":\"Work\"")
    }

    @Test
    fun buildSavedPointsGeoJsonEscapesQuotesInName() {
        val points = listOf(
            SavedPoint(id = "1", name = "The \"spot\"", latLng = LatLng(60.0, 10.0))
        )
        val json = buildSavedPointsGeoJson(points)
        assertContains(json, """The \"spot\"""")
    }

    @Test
    fun buildRulerLineGeoJsonProducesLineString() {
        val points = listOf(
            RulerPoint(LatLng(60.0, 10.0)),
            RulerPoint(LatLng(61.0, 11.0))
        )
        val json = buildRulerLineGeoJson(points)
        assertContains(json, "\"type\":\"LineString\"")
        assertContains(json, "[10.0,60.0]")
        assertContains(json, "[11.0,61.0]")
    }

    @Test
    fun buildRulerPointsGeoJsonProducesFeatureCollection() {
        val points = listOf(
            RulerPoint(LatLng(60.0, 10.0)),
            RulerPoint(LatLng(61.0, 11.0))
        )
        val json = buildRulerPointsGeoJson(points)
        assertContains(json, "\"type\":\"FeatureCollection\"")
        assertContains(json, "\"type\":\"Point\"")
        // Should have 2 features
        assertEquals(2, Regex("\"type\":\"Point\"").findAll(json).count())
    }

    @Test
    fun buildRulerPointsGeoJsonSinglePoint() {
        val points = listOf(RulerPoint(LatLng(60.0, 10.0)))
        val json = buildRulerPointsGeoJson(points)
        assertContains(json, "[10.0,60.0]")
        assertEquals(1, Regex("\"type\":\"Point\"").findAll(json).count())
    }

    @Test
    fun buildMeasurementLineGeoJsonProducesLineStringWithTwoCoords() {
        val m = TwoFingerMeasurement(
            lat1 = 60.0, lng1 = 10.0, lat2 = 61.0, lng2 = 11.0,
            distanceMeters = 500.0
        )
        val json = buildMeasurementLineGeoJson(m)
        assertContains(json, "\"type\":\"LineString\"")
        assertContains(json, "[[10.0,60.0],[11.0,61.0]]")
    }

    @Test
    fun buildMeasurementPointsGeoJsonProducesEndpointsAndLabel() {
        val m = TwoFingerMeasurement(
            lat1 = 60.0, lng1 = 10.0, lat2 = 62.0, lng2 = 14.0,
            distanceMeters = 500.0
        )
        val json = buildMeasurementPointsGeoJson(m)
        assertContains(json, "\"type\":\"FeatureCollection\"")
        assertEquals(2, Regex("\"role\":\"endpoint\"").findAll(json).count())
        assertContains(json, "\"coordinates\":[10.0,60.0]")
        assertContains(json, "\"coordinates\":[14.0,62.0]")
        assertContains(json, "\"role\":\"label\"")
        assertContains(json, "\"coordinates\":[12.0,61.0]")
        assertContains(json, "\"label\":\"${500.0.formatDistance()}\"")
    }

    @Test
    fun twoFingerMeasurementMidpointIsAverage() {
        val m = TwoFingerMeasurement(
            lat1 = 60.0, lng1 = 10.0, lat2 = 62.0, lng2 = 14.0,
            distanceMeters = 500.0
        )
        assertEquals(61.0, m.midLat)
        assertEquals(12.0, m.midLng)
    }

    @Test
    fun twoFingerMeasurementEndpointsAreTheTwoTouchPoints() {
        val m = TwoFingerMeasurement(
            lat1 = 60.0, lng1 = 10.0, lat2 = 62.0, lng2 = 14.0,
            distanceMeters = 500.0
        )
        val endpoints = m.endpoints
        assertEquals(2, endpoints.size)
        assertEquals(LatLng(60.0, 10.0), endpoints[0])
        assertEquals(LatLng(62.0, 14.0), endpoints[1])
    }

    @Test
    fun emptyListsProduceValidGeoJson() {
        val trackJson = buildTrackGeoJson(emptyList())
        assertContains(trackJson, "\"type\":\"LineString\"")

        val savedJson = buildSavedPointsGeoJson(emptyList())
        assertContains(savedJson, "\"type\":\"FeatureCollection\"")

        val rulerLineJson = buildRulerLineGeoJson(emptyList())
        assertContains(rulerLineJson, "\"type\":\"LineString\"")

        val rulerPointsJson = buildRulerPointsGeoJson(emptyList())
        assertContains(rulerPointsJson, "\"type\":\"FeatureCollection\"")
    }
}
