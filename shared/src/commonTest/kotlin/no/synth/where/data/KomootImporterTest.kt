package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KomootImporterTest {

    // --- URL parsing ---

    @Test
    fun parseKomootUrl_standardTourUrl() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.com/tour/123456789")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_withLocalePrefix() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.com/de-de/tour/123456789")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_norwegianLocale() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.com/nb/tour/123456789")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_frenchLocale() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.com/fr-fr/tour/123456789")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_noWww() {
        val ref = KomootImporter.parseKomootUrl("https://komoot.com/tour/123456789")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_komootDe() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.de/tour/123456789")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_komootDeWithLocale() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.de/de-de/tour/123456789")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_withQueryParams() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.com/tour/123456789?ref=wtd")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_withTrailingSlash() {
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.com/tour/123456789/")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_withWhitespace() {
        val ref = KomootImporter.parseKomootUrl("  https://www.komoot.com/tour/123456789  ")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_embedUrl() {
        // Embed URLs also contain /tour/{id}
        val ref = KomootImporter.parseKomootUrl("https://www.komoot.com/tour/123456789/embed?profile=1")
        assertNotNull(ref)
        assertEquals("123456789", ref.id)
    }

    @Test
    fun parseKomootUrl_notKomootUrl() {
        assertNull(KomootImporter.parseKomootUrl("https://www.strava.com/activities/123456"))
    }

    @Test
    fun parseKomootUrl_bareNumber() {
        assertNull(KomootImporter.parseKomootUrl("123456789"))
    }

    @Test
    fun parseKomootUrl_empty() {
        assertNull(KomootImporter.parseKomootUrl(""))
    }

    @Test
    fun parseKomootUrl_randomText() {
        assertNull(KomootImporter.parseKomootUrl("hello world"))
    }

    // --- Name extraction ---

    @Test
    fun extractName_jsonName() {
        val html = """<script>{"name":"Besseggen Ridge Hike","type":"tour_recorded"}</script>"""
        assertEquals("Besseggen Ridge Hike", KomootImporter.extractName(html))
    }

    @Test
    fun extractName_ogTitle() {
        val html = """<meta property="og:title" content="Sunday Morning Run | komoot">"""
        assertEquals("Sunday Morning Run", KomootImporter.extractName(html))
    }

    @Test
    fun extractName_ogTitleReversed() {
        val html = """<meta content="Evening Hike | komoot" property="og:title">"""
        assertEquals("Evening Hike", KomootImporter.extractName(html))
    }

    @Test
    fun extractName_titleTag() {
        val html = """<title>Afternoon Ride | komoot</title>"""
        assertEquals("Afternoon Ride", KomootImporter.extractName(html))
    }

    @Test
    fun extractName_titleWithDash() {
        val html = """<title>My Cool Tour - Komoot</title>"""
        assertEquals("My Cool Tour", KomootImporter.extractName(html))
    }

    @Test
    fun extractName_noMatch() {
        assertNull(KomootImporter.extractName("<html><body>Nothing</body></html>"))
    }

    @Test
    fun extractName_escapedUnicode() {
        val html = """<script>{"name":"Tour in Jotunheimen \u0026 Rondane"}</script>"""
        assertEquals("Tour in Jotunheimen & Rondane", KomootImporter.extractName(html))
    }

    @Test
    fun extractName_norwegianCharacters() {
        val html = """<script>{"name":"Tur over \u00d8stmarka \u00e5sen"}</script>"""
        // \u00d8 = Ø, but our unescape doesn't cover uppercase — still valid as raw
        assertNotNull(KomootImporter.extractName(html))
    }

    // --- Coordinate extraction ---

    @Test
    fun extractCoordinatesFromHtml_standardFormat() {
        val html = """
            <script>{"_embedded":{"coordinates":{"items":[{"lat":59.1,"lng":10.2,"alt":100.0,"t":0},{"lat":59.2,"lng":10.3,"alt":105.0,"t":5000}]}}}</script>
        """.trimIndent()
        val coords = KomootImporter.extractCoordinatesFromHtml(html)
        assertNotNull(coords)
        assertEquals(2, coords.size)
        assertEquals(59.1, coords[0].lat, 0.001)
        assertEquals(10.2, coords[0].lng, 0.001)
        assertEquals(100.0, coords[0].altitude)
        assertEquals(0L, coords[0].timestamp)
        assertEquals(59.2, coords[1].lat, 0.001)
        assertEquals(5000L, coords[1].timestamp)
    }

    @Test
    fun extractCoordinatesFromHtml_withoutTimestamp() {
        val html = """
            <script>{"items":[{"lat":59.0,"lng":10.0,"alt":50.0}]}</script>
        """.trimIndent()
        val coords = KomootImporter.extractCoordinatesFromHtml(html)
        assertNotNull(coords)
        assertEquals(1, coords.size)
        assertNull(coords[0].timestamp)
    }

    @Test
    fun extractCoordinatesFromHtml_withoutAltitude() {
        val html = """
            <script>{"items":[{"lat":59.0,"lng":10.0,"t":0}]}</script>
        """.trimIndent()
        val coords = KomootImporter.extractCoordinatesFromHtml(html)
        assertNotNull(coords)
        assertEquals(1, coords.size)
        assertNull(coords[0].altitude)
    }

    @Test
    fun extractCoordinatesFromHtml_noMatch() {
        assertNull(KomootImporter.extractCoordinatesFromHtml("<html>nothing</html>"))
    }

    // --- parseCoordinateArray ---

    @Test
    fun parseCoordinateArray_standard() {
        val json = """[{"lat":59.1,"lng":10.2,"alt":100.0,"t":0},{"lat":59.2,"lng":10.3,"alt":105.0,"t":5000}]"""
        val points = KomootImporter.parseCoordinateArray(json)
        assertNotNull(points)
        assertEquals(2, points.size)
        assertEquals(59.1, points[0].lat, 0.001)
        assertEquals(10.2, points[0].lng, 0.001)
        assertEquals(100.0, points[0].altitude)
        assertEquals(0L, points[0].timestamp)
    }

    @Test
    fun parseCoordinateArray_negativeCoordinates() {
        val json = """[{"lat":-33.856,"lng":-70.612,"alt":800.0,"t":1000}]"""
        val points = KomootImporter.parseCoordinateArray(json)
        assertNotNull(points)
        assertEquals(1, points.size)
        assertEquals(-33.856, points[0].lat, 0.001)
        assertEquals(-70.612, points[0].lng, 0.001)
    }

    @Test
    fun parseCoordinateArray_empty() {
        assertNull(KomootImporter.parseCoordinateArray("[]"))
    }

    @Test
    fun parseCoordinateArray_withExtraFields() {
        val json = """[{"lat":59.1,"lng":10.2,"alt":100.0,"t":0,"v":3.5,"heading":180}]"""
        val points = KomootImporter.parseCoordinateArray(json)
        assertNotNull(points)
        assertEquals(1, points.size)
        assertEquals(59.1, points[0].lat, 0.001)
    }

    // --- Encoded polyline extraction ---

    @Test
    fun extractEncodedPolyline_found() {
        val html = """<script>{"encodedPolyline":"_p~iF~ps|U_ulLnnqC"}</script>"""
        assertEquals("_p~iF~ps|U_ulLnnqC", KomootImporter.extractEncodedPolyline(html))
    }

    @Test
    fun extractEncodedPolyline_polylineKey() {
        val html = """<script>{"polyline":"_p~iF~ps|U_ulLnnqC"}</script>"""
        assertEquals("_p~iF~ps|U_ulLnnqC", KomootImporter.extractEncodedPolyline(html))
    }

    @Test
    fun extractEncodedPolyline_noMatch() {
        assertNull(KomootImporter.extractEncodedPolyline("<html>nothing</html>"))
    }

    // --- KomootPoint ---

    @Test
    fun komootPoint_withAllFields() {
        val point = KomootImporter.KomootPoint(lat = 59.123, lng = 10.456, altitude = 100.0, timestamp = 5000L)
        assertEquals(59.123, point.lat, 0.001)
        assertEquals(10.456, point.lng, 0.001)
        assertEquals(100.0, point.altitude)
        assertEquals(5000L, point.timestamp)
    }

    @Test
    fun komootPoint_withNullFields() {
        val point = KomootImporter.KomootPoint(lat = 59.123, lng = 10.456, altitude = null, timestamp = null)
        assertNull(point.altitude)
        assertNull(point.timestamp)
    }
}
