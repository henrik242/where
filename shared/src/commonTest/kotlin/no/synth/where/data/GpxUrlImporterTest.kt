package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxUrlImporterTest {

    @Test
    fun isGpxUrl_simpleGpxUrl() {
        assertTrue(GpxUrlImporter.isGpxUrl("https://example.com/track.gpx"))
    }

    @Test
    fun isGpxUrl_gpxUrlWithQueryParams() {
        assertTrue(GpxUrlImporter.isGpxUrl("https://example.com/track.gpx?token=abc123"))
    }

    @Test
    fun isGpxUrl_httpUrl() {
        assertTrue(GpxUrlImporter.isGpxUrl("http://files.example.org/my-hike.gpx"))
    }

    @Test
    fun isGpxUrl_caseInsensitive() {
        assertTrue(GpxUrlImporter.isGpxUrl("https://example.com/track.GPX"))
    }

    @Test
    fun isGpxUrl_withPath() {
        assertTrue(GpxUrlImporter.isGpxUrl("https://example.com/files/tracks/2024/summer.gpx"))
    }

    @Test
    fun isGpxUrl_notGpxExtension() {
        assertFalse(GpxUrlImporter.isGpxUrl("https://example.com/track.json"))
    }

    @Test
    fun isGpxUrl_gpxInPathButNotExtension() {
        assertFalse(GpxUrlImporter.isGpxUrl("https://example.com/gpx/track"))
    }

    @Test
    fun isGpxUrl_notAUrl() {
        assertFalse(GpxUrlImporter.isGpxUrl("1234567890"))
    }

    @Test
    fun isGpxUrl_stravaUrl() {
        assertFalse(GpxUrlImporter.isGpxUrl("https://www.strava.com/activities/1234567890"))
    }
}
