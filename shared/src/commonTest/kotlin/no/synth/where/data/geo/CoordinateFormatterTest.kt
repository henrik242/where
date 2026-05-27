package no.synth.where.data.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CoordinateFormatterTest {

    // Sample point shown in the settings-screen format preview.
    private val oslo = LatLng(59.9139, 10.7522)

    @Test
    fun formatLatLngOslo() {
        assertEquals("59.9139° N, 10.7522° E", CoordinateFormatter.formatLatLng(oslo))
    }

    @Test
    fun formatDmsOslo() {
        assertEquals("59°54'50.0\" N, 10°45'7.9\" E", CoordinateFormatter.formatDms(oslo))
    }

    @Test
    fun formatUtmOslo() {
        assertEquals("32V 597980 6643119", CoordinateFormatter.formatUtm(oslo))
    }

    @Test
    fun formatMgrsOslo() {
        assertEquals("32V NM 97980 43119", CoordinateFormatter.formatMgrs(oslo))
    }

    @Test
    fun formatLatLngSouthernHemisphereWesternLongitude() {
        val buenosAires = LatLng(-34.6037, -58.3816)
        assertEquals("34.6037° S, 58.3816° W", CoordinateFormatter.formatLatLng(buenosAires))
    }

    @Test
    fun formatDmsCarriesSecondsIntoMinutes() {
        // Verifies the rounded-60s guard: a value just under a whole degree must not render as `60.0"`.
        val nearWhole = LatLng(59.99999, 10.99999)
        val result = CoordinateFormatter.formatDms(nearWhole)
        assertFalse(result.contains("60.0\""), "DMS seconds rounded to 60 leaked through: $result")
    }
}
