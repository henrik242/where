package no.synth.where.ui.map

import no.synth.where.data.RulerPoint
import no.synth.where.data.SavedPoint
import no.synth.where.data.TrackPoint
import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapGeoJsonTest {

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
