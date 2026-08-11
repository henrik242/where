package no.synth.where.integration

import no.synth.where.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test that makes real HTTP requests to UT.no.
 * Run with: ../gradlew integrationTest
 */
class UtNoFetchTest {

    private val utNoUrls = listOf(
        "https://ut.no/turforslag/1113800/01-hystadmarkjo",
        "https://ut.no/kart/tur/119605",
        "https://ut.no/rutebeskrivelse/135551",
        "https://ut.no/api/gpx/trip/1113800",
    )

    @Before
    fun setUp() = IntegrationTestSupport.plantLogger()

    @Test
    fun importRoute_getsTrackWithPoints() = runBlocking {
        val importer = UtNoImporter(IntegrationTestSupport.makeClient())
        for (url in utNoUrls) {
            val track = importer.importFromUrl(url, addElevation = false)
            println("Track ($url): name=${track?.name}, points=${track?.points?.size}")
            checkNotNull(track) { "Expected to import track from $url but got null" }
            assertTrue("Expected track from $url to have points", track.points.isNotEmpty())
            assertTrue("Expected track from $url to have a name", track.name.isNotBlank())
        }
    }
}
