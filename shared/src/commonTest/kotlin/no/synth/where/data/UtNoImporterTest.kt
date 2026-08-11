package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun parseUtNoUrl_kartTurforslag() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/kart/turforslag/1234567/besseggen")
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

    // --- URL parsing: the GPX endpoint itself ---

    @Test
    fun parseUtNoUrl_gpxApiTrip() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/api/gpx/trip/1113800")
        assertNotNull(ref)
        assertEquals(1113800, ref.id)
        assertEquals(UtNoImporter.UtNoType.TRIP, ref.type)
    }

    @Test
    fun parseUtNoUrl_gpxApiRoute() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/api/gpx/route/135551")
        assertNotNull(ref)
        assertEquals(135551, ref.id)
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
    fun parseUtNoUrl_withWwwPrefix() {
        val ref = UtNoImporter.parseUtNoUrl("https://www.ut.no/turforslag/1234567/besseggen")
        assertNotNull(ref)
        assertEquals(1234567, ref.id)
    }

    @Test
    fun parseUtNoUrl_withTrailingPathSegment() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/rutebeskrivelse/136969/fra-a-til-b/kart")
        assertNotNull(ref)
        assertEquals(136969, ref.id)
        assertEquals(UtNoImporter.UtNoType.ROUTE, ref.type)
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
    fun parseUtNoUrl_uppercaseHost() {
        val ref = UtNoImporter.parseUtNoUrl("https://UT.NO/Turforslag/1234567")
        assertNotNull(ref)
        assertEquals(1234567, ref.id)
        assertEquals(UtNoImporter.UtNoType.TRIP, ref.type)
    }

    @Test
    fun parseUtNoUrl_invalidUrl() {
        assertNull(UtNoImporter.parseUtNoUrl("https://example.com/turforslag/123"))
    }

    @Test
    fun parseUtNoUrl_unknownPath() {
        assertNull(UtNoImporter.parseUtNoUrl("https://ut.no/hytte/1234567/gjendebu"))
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

    @Test
    fun parseUtNoUrl_idOutOfIntRange() {
        assertNull(UtNoImporter.parseUtNoUrl("https://ut.no/turforslag/99999999999"))
    }

    // --- GPX endpoint URL ---

    @Test
    fun gpxUrl_trip() {
        val ref = UtNoImporter.UtNoRef(1113800, UtNoImporter.UtNoType.TRIP)
        assertEquals("https://ut.no/api/gpx/trip/1113800", UtNoImporter.gpxUrl(ref))
    }

    @Test
    fun gpxUrl_route() {
        val ref = UtNoImporter.UtNoRef(135551, UtNoImporter.UtNoType.ROUTE)
        assertEquals("https://ut.no/api/gpx/route/135551", UtNoImporter.gpxUrl(ref))
    }

    @Test
    fun gpxUrl_roundTripsFromPageUrl() {
        val ref = UtNoImporter.parseUtNoUrl("https://ut.no/turforslag/1113800/01-hystadmarkjo")
        assertNotNull(ref)
        assertEquals("https://ut.no/api/gpx/trip/1113800", UtNoImporter.gpxUrl(ref))
    }
}
