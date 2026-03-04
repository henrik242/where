package no.synth.where.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtNoImporterTest {

    // --- URL parsing: turforslag (TRIP) ---

    @Test
    fun parseUtNoUrl_turforslag() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/turforslag/1234567/besseggen")
        assertNotNull(ref)
        assertEquals(1234567, ref.id)
        assertEquals(UtNoImporter.UtNoType.TRIP, ref.type)
    }

    @Test
    fun parseUtNoUrl_kartTur() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/kart/tur/1234567/besseggen#12/61.5/8.8")
        assertNotNull(ref)
        assertEquals(1234567, ref.id)
        assertEquals(UtNoImporter.UtNoType.TRIP, ref.type)
    }

    // --- URL parsing: rutebeskrivelse (ROUTE) ---

    @Test
    fun parseUtNoUrl_rutebeskrivelse() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/rutebeskrivelse/7654321/trolltunga")
        assertNotNull(ref)
        assertEquals(7654321, ref.id)
        assertEquals(UtNoImporter.UtNoType.ROUTE, ref.type)
    }

    @Test
    fun parseUtNoUrl_kartRutebeskrivelse() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/kart/rutebeskrivelse/7654321/trolltunga#10/60.1/6.7")
        assertNotNull(ref)
        assertEquals(7654321, ref.id)
        assertEquals(UtNoImporter.UtNoType.ROUTE, ref.type)
    }

    // --- URL parsing: edge cases ---

    @Test
    fun parseUtNoUrl_withoutSlug() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/turforslag/1234567")
        assertNotNull(ref)
        assertEquals(1234567, ref.id)
    }

    @Test
    fun parseUtNoUrl_withQueryParams() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/turforslag/1234567/slug?ref=share")
        assertNotNull(ref)
        assertEquals(1234567, ref.id)
    }

    @Test
    fun parseUtNoUrl_withWhitespace() {
        val ref = UtNoImporter.parseUtNoUrl("  https://ut.no/turforslag/1234567/slug  ")
        assertNotNull(ref)
        assertEquals(1234567, ref.id)
    }

    @Test
    fun parseUtNoUrl_invalidUrl() {
        assertNull(UtNoImporter.parseUtNoUrl("https://example.com/turforslag/123"))
    }

    @Test
    fun parseUtNoUrl_empty() {
        assertNull(UtNoImporter.parseUtNoUrl(""))
    }

    @Test
    fun parseUtNoUrl_randomText() {
        assertNull(UtNoImporter.parseUtNoUrl("hello world"))
    }

    @Test
    fun parseUtNoUrl_noId() {
        assertNull(UtNoImporter.parseUtNoUrl("https://ut.no/turforslag/"))
    }

    // --- GeoJSON coordinate parsing ---

    @Test
    fun parseGeoJsonCoordinates_withElevation() {
        val geojson = Json.parseToJsonElement("""
            {"type":"LineString","coordinates":[[8.123,61.456,1200.0],[8.124,61.457,1205.5]]}
        """.trimIndent()).jsonObject
        val points = UtNoImporter.parseGeoJsonCoordinates(geojson)
        assertEquals(2, points.size)
        assertEquals(61.456, points[0].first, 0.001)
        assertEquals(8.123, points[0].second, 0.001)
        assertEquals(1200.0, points[0].third)
        assertEquals(61.457, points[1].first, 0.001)
        assertEquals(1205.5, points[1].third)
    }

    @Test
    fun parseGeoJsonCoordinates_withoutElevation() {
        val geojson = Json.parseToJsonElement("""
            {"type":"LineString","coordinates":[[8.123,61.456],[8.124,61.457]]}
        """.trimIndent()).jsonObject
        val points = UtNoImporter.parseGeoJsonCoordinates(geojson)
        assertEquals(2, points.size)
        assertEquals(61.456, points[0].first, 0.001)
        assertEquals(8.123, points[0].second, 0.001)
        assertNull(points[0].third)
    }

    @Test
    fun parseGeoJsonCoordinates_emptyCoordinates() {
        val geojson = Json.parseToJsonElement("""
            {"type":"LineString","coordinates":[]}
        """.trimIndent()).jsonObject
        val points = UtNoImporter.parseGeoJsonCoordinates(geojson)
        assertTrue(points.isEmpty())
    }

    @Test
    fun parseGeoJsonCoordinates_noCoordinatesField() {
        val geojson = Json.parseToJsonElement("""{"type":"LineString"}""").jsonObject
        val points = UtNoImporter.parseGeoJsonCoordinates(geojson)
        assertTrue(points.isEmpty())
    }

    @Test
    fun parseGeoJsonCoordinates_singlePointSkipped() {
        val geojson = Json.parseToJsonElement("""
            {"type":"LineString","coordinates":[[8.123]]}
        """.trimIndent()).jsonObject
        val points = UtNoImporter.parseGeoJsonCoordinates(geojson)
        assertTrue(points.isEmpty())
    }
}
