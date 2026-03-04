package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackUrlImporterTest {

    @Test
    fun detectService_stravaComUrl() {
        assertEquals(TrackUrlImporter.Service.STRAVA, TrackUrlImporter.detectService("https://www.strava.com/activities/1234567890"))
    }

    @Test
    fun detectService_stravaAppLink() {
        assertEquals(TrackUrlImporter.Service.STRAVA, TrackUrlImporter.detectService("https://strava.app.link/abc123XYZ"))
    }

    @Test
    fun detectService_stravaAppLinkWithQueryParams() {
        assertEquals(TrackUrlImporter.Service.STRAVA, TrackUrlImporter.detectService("https://strava.app.link/xyz?deep_link_value=something"))
    }

    @Test
    fun detectService_garminUrl() {
        assertEquals(TrackUrlImporter.Service.GARMIN, TrackUrlImporter.detectService("https://connect.garmin.com/modern/activity/12345"))
    }

    @Test
    fun detectService_komootUrl() {
        assertEquals(TrackUrlImporter.Service.KOMOOT, TrackUrlImporter.detectService("https://www.komoot.com/tour/12345"))
    }

    @Test
    fun detectService_bareNumericId() {
        assertEquals(TrackUrlImporter.Service.STRAVA, TrackUrlImporter.detectService("1234567890"))
    }

    @Test
    fun detectService_utNoTurforslagUrl() {
        assertEquals(TrackUrlImporter.Service.UT_NO, TrackUrlImporter.detectService("https://ut.no/turforslag/1234567/besseggen"))
    }

    @Test
    fun detectService_utNoRutebeskrivelseUrl() {
        assertEquals(TrackUrlImporter.Service.UT_NO, TrackUrlImporter.detectService("https://ut.no/rutebeskrivelse/7654321/trolltunga"))
    }

    @Test
    fun detectService_utNoKartTurUrl() {
        assertEquals(TrackUrlImporter.Service.UT_NO, TrackUrlImporter.detectService("https://ut.no/kart/tur/1234567/slug#12/61.5/8.8"))
    }

    @Test
    fun detectService_unknownUrl() {
        assertEquals(TrackUrlImporter.Service.UNKNOWN, TrackUrlImporter.detectService("https://example.com/something"))
    }
}
