package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StravaImporterTest {

    // --- Polyline decoding ---

    @Test
    fun decodePolyline_simple() {
        // Encodes roughly (38.5, -120.2) -> (40.7, -120.95) -> (43.252, -126.453)
        val points = StravaImporter.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.001)
        assertEquals(-120.2, points[0].longitude, 0.001)
        assertEquals(40.7, points[1].latitude, 0.001)
        assertEquals(-120.95, points[1].longitude, 0.001)
        assertEquals(43.252, points[2].latitude, 0.001)
        assertEquals(-126.453, points[2].longitude, 0.001)
    }

    @Test
    fun decodePolyline_empty() {
        assertTrue(StravaImporter.decodePolyline("").isEmpty())
    }

    @Test
    fun decodePolyline_singlePoint() {
        val points = StravaImporter.decodePolyline("_p~iF~ps|U")
        assertEquals(1, points.size)
        assertTrue(points[0].latitude in 38.0..39.0)
        assertTrue(points[0].longitude in -121.0..-120.0)
    }

    // --- URL parsing: activities ---

    @Test
    fun parseStravaUrl_activityUrl() {
        val ref = StravaImporter.parseStravaUrl("https://www.strava.com/activities/1234567890")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("1234567890", ref.id)
    }

    @Test
    fun parseStravaUrl_activityUrlWithQueryParams() {
        val ref = StravaImporter.parseStravaUrl("https://www.strava.com/activities/1234567890?share=true")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("1234567890", ref.id)
    }

    @Test
    fun parseStravaUrl_activityUrlWithTrailingSlash() {
        val ref = StravaImporter.parseStravaUrl("https://www.strava.com/activities/1234567890/")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("1234567890", ref.id)
    }

    @Test
    fun parseStravaUrl_activityUrlNoWww() {
        val ref = StravaImporter.parseStravaUrl("https://strava.com/activities/1234567890")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("1234567890", ref.id)
    }

    // --- URL parsing: routes ---

    @Test
    fun parseStravaUrl_routeUrl() {
        val ref = StravaImporter.parseStravaUrl("https://www.strava.com/routes/9876543210")
        assertNotNull(ref)
        assertEquals("route", ref.type)
        assertEquals("9876543210", ref.id)
    }

    @Test
    fun parseStravaUrl_routeUrlWithQueryParams() {
        val ref = StravaImporter.parseStravaUrl("https://www.strava.com/routes/9876543210?map_type=terrain")
        assertNotNull(ref)
        assertEquals("route", ref.type)
        assertEquals("9876543210", ref.id)
    }

    @Test
    fun parseStravaUrl_routeUrlNoWww() {
        val ref = StravaImporter.parseStravaUrl("https://strava.com/routes/9876543210")
        assertNotNull(ref)
        assertEquals("route", ref.type)
        assertEquals("9876543210", ref.id)
    }

    // --- URL parsing: bare IDs and edge cases ---

    @Test
    fun parseStravaUrl_bareNumericId() {
        val ref = StravaImporter.parseStravaUrl("1234567890")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("1234567890", ref.id)
    }

    @Test
    fun parseStravaUrl_bareNumericIdWithWhitespace() {
        val ref = StravaImporter.parseStravaUrl("  1234567890  ")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("1234567890", ref.id)
    }

    @Test
    fun parseStravaUrl_tooShortNumber() {
        assertNull(StravaImporter.parseStravaUrl("123"))
    }

    @Test
    fun parseStravaUrl_notAUrl() {
        assertNull(StravaImporter.parseStravaUrl("not-a-url"))
    }

    @Test
    fun parseStravaUrl_empty() {
        assertNull(StravaImporter.parseStravaUrl(""))
    }

    // --- Polyline extraction from HTML ---

    @Test
    fun extractPolyline_polylineKey() {
        val html = """<script>{"map":{"polyline":"_p~iF~ps|U_ulLnnqC"}}</script>"""
        assertEquals("_p~iF~ps|U_ulLnnqC", StravaImporter.extractPolyline(html))
    }

    @Test
    fun extractPolyline_summaryPolyline() {
        val html = """<script>{"summary_polyline":"abc123def456"}</script>"""
        assertEquals("abc123def456", StravaImporter.extractPolyline(html))
    }

    @Test
    fun extractPolyline_camelCase() {
        val html = """<script>{"summaryPolyline":"abc123def456"}</script>"""
        assertEquals("abc123def456", StravaImporter.extractPolyline(html))
    }

    @Test
    fun extractPolyline_dataAttribute() {
        val html = """<div data-polyline="abc123def456"></div>"""
        assertEquals("abc123def456", StravaImporter.extractPolyline(html))
    }

    @Test
    fun extractPolyline_dataSummaryPolylineAttribute() {
        val html = """<div data-summary-polyline="abc123def456"></div>"""
        assertEquals("abc123def456", StravaImporter.extractPolyline(html))
    }

    @Test
    fun extractPolyline_prefersFullPolylineOverSummary() {
        // If both "polyline" and "summary_polyline" exist, should pick "polyline" first (higher detail)
        val html = """<script>{"polyline":"full_detail","summary_polyline":"summary_only"}</script>"""
        assertEquals("full_detail", StravaImporter.extractPolyline(html))
    }

    @Test
    fun extractPolyline_noMatch() {
        assertNull(StravaImporter.extractPolyline("<html><body>No polyline here</body></html>"))
    }

    @Test
    fun extractPolyline_escapedBackslashes() {
        val html = """<script>{"polyline":"abc\\def"}</script>"""
        val result = StravaImporter.extractPolyline(html)
        assertNotNull(result)
        assertEquals("abc\\def", result)
    }

    // --- __NEXT_DATA__ extraction ---

    @Test
    fun extractFromNextData_basic() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"activity":{"name":"Morning Run","streams":{"location":[{"lat":59.1,"lng":10.2},{"lat":59.2,"lng":10.3}],"elevation":[34,35]}}}}}</script>"""
        val result = StravaImporter.extractFromNextData(html)
        assertNotNull(result)
        assertEquals("Morning Run", result.name)
        assertEquals(2, result.points.size)
        assertEquals(59.1, result.points[0].latitude, 0.001)
        assertEquals(10.2, result.points[0].longitude, 0.001)
        assertEquals(34.0, result.elevations[0])
        assertEquals(35.0, result.elevations[1])
    }

    @Test
    fun extractFromNextData_noLocation() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"activity":{"name":"Run","streams":{"distance":[1,2,3]}}}}}</script>"""
        assertNull(StravaImporter.extractFromNextData(html))
    }

    @Test
    fun extractFromNextData_noNextData() {
        assertNull(StravaImporter.extractFromNextData("<html><body>No script here</body></html>"))
    }

    @Test
    fun extractFromNextData_noElevationFallsBackToNulls() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"activity":{"streams":{"location":[{"lat":59.1,"lng":10.2}]}}}}}</script>"""
        val result = StravaImporter.extractFromNextData(html)
        assertNotNull(result)
        assertEquals(1, result.points.size)
        assertEquals(1, result.elevations.size)
        assertNull(result.elevations[0])
    }

    // --- Canonical URL extraction ---

    @Test
    fun extractCanonicalUrl_canonicalLink() {
        val html = """<link rel="canonical" href="https://www.strava.com/activities/1234567890">"""
        assertEquals("https://www.strava.com/activities/1234567890", StravaImporter.extractCanonicalUrl(html))
    }

    @Test
    fun extractCanonicalUrl_ogUrl() {
        val html = """<meta property="og:url" content="https://www.strava.com/routes/9876543210">"""
        assertEquals("https://www.strava.com/routes/9876543210", StravaImporter.extractCanonicalUrl(html))
    }

    @Test
    fun extractCanonicalUrl_prefersCanonicalLinkOverOgUrl() {
        val html = """
            <link rel="canonical" href="https://www.strava.com/activities/111">
            <meta property="og:url" content="https://www.strava.com/activities/222">
        """.trimIndent()
        assertEquals("https://www.strava.com/activities/111", StravaImporter.extractCanonicalUrl(html))
    }

    @Test
    fun extractCanonicalUrl_noMatch() {
        assertNull(StravaImporter.extractCanonicalUrl("<html><body>No canonical here</body></html>"))
    }

    // --- Name extraction ---

    @Test
    fun extractName_ogTitle_activity() {
        val html = """<meta property="og:title" content="Morning Run on Strava">"""
        assertEquals("Morning Run", StravaImporter.extractName(html))
    }

    @Test
    fun extractName_ogTitle_route() {
        val html = """<meta property="og:title" content="Besseggen Ridge | Strava Route">"""
        assertEquals("Besseggen Ridge", StravaImporter.extractName(html))
    }

    @Test
    fun extractName_ogTitle_reversedAttributes() {
        val html = """<meta content="Afternoon Ride on Strava" property="og:title">"""
        assertEquals("Afternoon Ride", StravaImporter.extractName(html))
    }

    @Test
    fun extractName_titleTag() {
        val html = """<title>Evening Hike on Strava</title>"""
        assertEquals("Evening Hike", StravaImporter.extractName(html))
    }

    @Test
    fun extractName_titleTagWithPipe() {
        val html = """<title>Morning Run | Run | Strava</title>"""
        assertEquals("Morning Run", StravaImporter.extractName(html))
    }

    @Test
    fun extractName_jsonName() {
        val html = """<script>{"name":"Lunch Run","type":"Run"}</script>"""
        assertEquals("Lunch Run", StravaImporter.extractName(html))
    }

    @Test
    fun extractName_prefersOgTitleOverJson() {
        val html = """
            <meta property="og:title" content="The Real Name on Strava">
            <script>{"name":"JSON Name"}</script>
        """.trimIndent()
        assertEquals("The Real Name", StravaImporter.extractName(html))
    }

    @Test
    fun extractName_noMatch() {
        assertNull(StravaImporter.extractName("<html><body>No name here</body></html>"))
    }
}
