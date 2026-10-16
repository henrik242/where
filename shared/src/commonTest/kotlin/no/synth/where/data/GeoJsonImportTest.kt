package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoJsonImportTest {

    private val mixed = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": { "name": "Tur til Kolsåstoppen", "time": "2024-05-01T10:00:00Z" },
              "geometry": { "type": "LineString", "coordinates": [[10.50, 59.91, 120.0], [10.51, 59.92, 180.5], [10.52, 59.93]] }
            },
            {
              "type": "Feature",
              "properties": { "name": "Toppen", "description": "Utsikt", "time": "2024-05-01T10:10:00Z" },
              "geometry": { "type": "Point", "coordinates": [10.52, 59.93, 380] }
            },
            {
              "type": "Feature",
              "properties": null,
              "geometry": { "type": "Point", "coordinates": [10.53, 59.94] }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun readsTheLineAsTrack() {
        val track = assertNotNull(Track.fromBytes(mixed.encodeToByteArray()))
        assertEquals("Tur til Kolsåstoppen", track.name)
        assertEquals(3, track.points.size)
        assertEquals(59.91, track.points[0].latLng.latitude)
        assertEquals(10.50, track.points[0].latLng.longitude)
        assertEquals(180.5, track.points[1].altitude)
        assertNull(track.points[2].altitude)
        assertEquals(1714557600000, track.startTime)
    }

    @Test
    fun readsPointsAsWaypoints() {
        val points = parseWaypoints(mixed.encodeToByteArray())
        assertEquals(2, points.size)
        assertEquals("Toppen", points[0].name)
        assertEquals("Utsikt", points[0].description)
        assertEquals(1714558200000, points[0].timestamp)
        assertEquals(GpxWaypoints.DEFAULT_NAME, points[1].name)
        assertEquals(59.94, points[1].latLng.latitude)
    }

    // ogr2ogr's GPX-to-GeoJSON output names the collection after the layer.
    @Test
    fun prefersFeatureNamesOverTheLayerName() {
        val json = """
            {"type": "FeatureCollection", "name": "tracks", "features": [
              {"type": "Feature", "properties": {"name": "Morning Ride"},
               "geometry": {"type": "MultiLineString", "coordinates": [[[10.0, 60.0]], [[10.1, 60.1], [10.2, 60.2]]]}}
            ]}
        """.trimIndent()
        val track = assertNotNull(GeoJsonImport.parseTrack(json))
        assertEquals("Morning Ride", track.name)
        assertEquals(3, track.points.size)
    }

    @Test
    fun fallsBackToPointsWhenThereAreNoLines() {
        val json = """{"type": "MultiPoint", "coordinates": [[10.0, 60.0], [10.1, 60.1]]}"""
        val track = assertNotNull(GeoJsonImport.parseTrack(json))
        assertEquals(2, track.points.size)
        assertEquals(Track.DEFAULT_IMPORT_NAME, track.name)
    }

    @Test
    fun skipsOutOfRangeAndMalformedPositions() {
        val json = """{"type": "LineString", "coordinates": [[10.0, 95.0], ["x", 60.0], [10.0], [10.0, 60.0]]}"""
        assertEquals(1, assertNotNull(GeoJsonImport.parseTrack(json)).points.size)
    }

    @Test
    fun rejectsInvalidJsonAndEmptyCollections() {
        assertNull(GeoJsonImport.parseTrack("{\"type\": \"FeatureCollection\", "))
        assertNull(GeoJsonImport.parseTrack("{\"type\": \"FeatureCollection\", \"features\": []}"))
        assertTrue(GeoJsonImport.parseWaypoints("{\"type\": \"FeatureCollection\", \"features\": []}").isEmpty())
    }

    @Test
    fun readsAFileStartingWithAByteOrderMark() {
        assertNotNull(Track.fromBytes(("﻿" + mixed).encodeToByteArray()))
    }
}
