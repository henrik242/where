package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarminImporterTest {

    // --- URL parsing: activities ---

    @Test
    fun parseGarminUrl_modernActivityUrl() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/modern/activity/12345678901")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("12345678901", ref.id)
    }

    @Test
    fun parseGarminUrl_modernActivityUrlWithTrailingSlash() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/modern/activity/12345678901/")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("12345678901", ref.id)
    }

    @Test
    fun parseGarminUrl_modernActivityUrlWithQueryParams() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/modern/activity/12345678901?share=true")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("12345678901", ref.id)
    }

    @Test
    fun parseGarminUrl_appActivityUrl() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/app/activity/12345678901")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("12345678901", ref.id)
    }

    @Test
    fun parseGarminUrl_appCourseUrl() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/app/course/421983675")
        assertNotNull(ref)
        assertEquals("course", ref.type)
        assertEquals("421983675", ref.id)
    }

    @Test
    fun parseGarminUrl_legacyActivityUrl() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/activity/12345678901")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("12345678901", ref.id)
    }

    @Test
    fun parseGarminUrl_modernActivityWithWhitespace() {
        val ref = GarminImporter.parseGarminUrl("  https://connect.garmin.com/modern/activity/12345678901  ")
        assertNotNull(ref)
        assertEquals("activity", ref.type)
        assertEquals("12345678901", ref.id)
    }

    // --- URL parsing: courses ---

    @Test
    fun parseGarminUrl_modernCourseUrl() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/modern/course/98765432")
        assertNotNull(ref)
        assertEquals("course", ref.type)
        assertEquals("98765432", ref.id)
    }

    @Test
    fun parseGarminUrl_legacyCourseUrl() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/course/98765432")
        assertNotNull(ref)
        assertEquals("course", ref.type)
        assertEquals("98765432", ref.id)
    }

    @Test
    fun parseGarminUrl_courseWithQueryParams() {
        val ref = GarminImporter.parseGarminUrl("https://connect.garmin.com/modern/course/98765432?mapType=terrain")
        assertNotNull(ref)
        assertEquals("course", ref.type)
        assertEquals("98765432", ref.id)
    }

    // --- URL parsing: edge cases ---

    @Test
    fun parseGarminUrl_notAGarminUrl() {
        assertNull(GarminImporter.parseGarminUrl("https://www.strava.com/activities/123456"))
    }

    @Test
    fun parseGarminUrl_bareNumber() {
        // Bare numbers are NOT matched (ambiguous — could be Strava)
        assertNull(GarminImporter.parseGarminUrl("12345678901"))
    }

    @Test
    fun parseGarminUrl_empty() {
        assertNull(GarminImporter.parseGarminUrl(""))
    }

    @Test
    fun parseGarminUrl_randomText() {
        assertNull(GarminImporter.parseGarminUrl("hello world"))
    }

    // --- Name extraction ---

    @Test
    fun extractName_activityName() {
        val html = """<script>{"activityName":"Morning Trail Run","activityId":123}</script>"""
        assertEquals("Morning Trail Run", GarminImporter.extractName(html))
    }

    @Test
    fun extractName_courseName() {
        val html = """<script>{"courseName":"Besseggen Ridge Loop","courseId":456}</script>"""
        assertEquals("Besseggen Ridge Loop", GarminImporter.extractName(html))
    }

    @Test
    fun extractName_ogTitle() {
        val html = """<meta property="og:title" content="Afternoon Hike | Garmin Connect">"""
        assertEquals("Afternoon Hike", GarminImporter.extractName(html))
    }

    @Test
    fun extractName_ogTitleReversedAttributes() {
        val html = """<meta content="Evening Run | Garmin Connect" property="og:title">"""
        assertEquals("Evening Run", GarminImporter.extractName(html))
    }

    @Test
    fun extractName_titleTag() {
        val html = """<title>Lunch Ride | Garmin Connect</title>"""
        assertEquals("Lunch Ride", GarminImporter.extractName(html))
    }

    @Test
    fun extractName_titleTagWithDash() {
        val html = """<title>Sunday Long Run - Garmin Connect</title>"""
        assertEquals("Sunday Long Run", GarminImporter.extractName(html))
    }

    @Test
    fun extractName_prefersActivityNameOverOgTitle() {
        val html = """
            <meta property="og:title" content="Generic Title | Garmin Connect">
            <script>{"activityName":"Specific Activity Name"}</script>
        """.trimIndent()
        assertEquals("Specific Activity Name", GarminImporter.extractName(html))
    }

    @Test
    fun extractName_noMatch() {
        assertNull(GarminImporter.extractName("<html><body>No name here</body></html>"))
    }

    @Test
    fun extractName_escapedCharacters() {
        val html = """<script>{"activityName":"Tur i Jotunheimen \u0026 Rondane"}</script>"""
        assertEquals("Tur i Jotunheimen & Rondane", GarminImporter.extractName(html))
    }

    // --- Coordinate extraction from HTML ---

    @Test
    fun extractCoordinatesFromHtml_geoPolylineDTO() {
        val html = """
            <script>{"geoPolylineDTO":{"polyline":[{"lat":59.123,"lon":10.456,"altitude":100.0},{"lat":59.124,"lon":10.457,"altitude":101.5}]}}</script>
        """.trimIndent()
        val coords = GarminImporter.extractCoordinatesFromHtml(html)
        assertNotNull(coords)
        assertEquals(2, coords.size)
        assertEquals(59.123, coords[0].lat, 0.001)
        assertEquals(10.456, coords[0].lon, 0.001)
        assertEquals(100.0, coords[0].altitude)
        assertEquals(59.124, coords[1].lat, 0.001)
        assertEquals(10.457, coords[1].lon, 0.001)
        assertEquals(101.5, coords[1].altitude)
    }

    @Test
    fun extractCoordinatesFromHtml_withoutAltitude() {
        val html = """
            <script>{"polyline":[{"lat":59.0,"lon":10.0},{"lat":59.1,"lon":10.1}]}</script>
        """.trimIndent()
        val coords = GarminImporter.extractCoordinatesFromHtml(html)
        assertNotNull(coords)
        assertEquals(2, coords.size)
        assertNull(coords[0].altitude)
        assertNull(coords[1].altitude)
    }

    @Test
    fun extractCoordinatesFromHtml_noPolylineArray() {
        // "polyline" exists but as a string, not an array — should not match
        val html = """<script>{"polyline":"_p~iF~ps|U"}</script>"""
        assertNull(GarminImporter.extractCoordinatesFromHtml(html))
    }

    @Test
    fun extractCoordinatesFromHtml_noMatch() {
        assertNull(GarminImporter.extractCoordinatesFromHtml("<html><body>Nothing here</body></html>"))
    }

    // --- parseLatLonArray ---

    @Test
    fun parseLatLonArray_standard() {
        val json = """[{"lat":59.1,"lon":10.2,"altitude":50.0},{"lat":59.2,"lon":10.3,"altitude":55.0}]"""
        val points = GarminImporter.parseLatLonArray(json)
        assertNotNull(points)
        assertEquals(2, points.size)
        assertEquals(59.1, points[0].lat, 0.001)
        assertEquals(10.2, points[0].lon, 0.001)
        assertEquals(50.0, points[0].altitude)
    }

    @Test
    fun parseLatLonArray_withExtraFields() {
        val json = """[{"lat":59.1,"lon":10.2,"altitude":50.0,"speed":3.5,"valid":true}]"""
        val points = GarminImporter.parseLatLonArray(json)
        assertNotNull(points)
        assertEquals(1, points.size)
        assertEquals(59.1, points[0].lat, 0.001)
    }

    @Test
    fun parseLatLonArray_negativeCoordinates() {
        val json = """[{"lat":-33.856,"lon":-70.612,"altitude":800.0}]"""
        val points = GarminImporter.parseLatLonArray(json)
        assertNotNull(points)
        assertEquals(1, points.size)
        assertEquals(-33.856, points[0].lat, 0.001)
        assertEquals(-70.612, points[0].lon, 0.001)
    }

    @Test
    fun parseLatLonArray_reversedLonLat() {
        val json = """[{"lon":10.2,"lat":59.1}]"""
        val points = GarminImporter.parseLatLonArray(json)
        assertNotNull(points)
        assertEquals(1, points.size)
        assertEquals(59.1, points[0].lat, 0.001)
        assertEquals(10.2, points[0].lon, 0.001)
    }

    @Test
    fun parseLatLonArray_empty() {
        assertNull(GarminImporter.parseLatLonArray("[]"))
    }

    // --- Encoded polyline extraction ---

    @Test
    fun extractEncodedPolyline_found() {
        val html = """<script>{"encodedPolyline":"_p~iF~ps|U_ulLnnqC"}</script>"""
        assertEquals("_p~iF~ps|U_ulLnnqC", GarminImporter.extractEncodedPolyline(html))
    }

    @Test
    fun extractEncodedPolyline_snakeCase() {
        val html = """<script>{"encoded_polyline":"_p~iF~ps|U_ulLnnqC"}</script>"""
        assertEquals("_p~iF~ps|U_ulLnnqC", GarminImporter.extractEncodedPolyline(html))
    }

    @Test
    fun extractEncodedPolyline_noMatch() {
        assertNull(GarminImporter.extractEncodedPolyline("<html>nothing</html>"))
    }

    // --- GarminPoint ---

    @Test
    fun garminPoint_toLatLng() {
        val point = GarminImporter.GarminPoint(lat = 59.123, lon = 10.456, altitude = 100.0)
        val latLng = point.toLatLng()
        assertEquals(59.123, latLng.latitude, 0.001)
        assertEquals(10.456, latLng.longitude, 0.001)
    }
}
