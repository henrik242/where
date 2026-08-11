package no.synth.where.integration

import no.synth.where.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test that makes real HTTP requests to Komoot.
 * Run with: ../gradlew integrationTest
 */
class KomootFetchTest {

    private val komootUrls = listOf(
        "https://www.komoot.com/tour/946150811",
    )

    @Before
    fun setUp() = IntegrationTestSupport.plantLogger()

    @Test
    fun importTour_getsTrackWithPoints() = runBlocking {
        val importer = KomootImporter(IntegrationTestSupport.makeClient())
        for (url in komootUrls) {
            val track = importer.importFromUrl(url, addElevation = false)
            println("Track ($url): name=${track?.name}, points=${track?.points?.size}")
            checkNotNull(track) { "Expected to import track from $url but got null" }
            assertTrue("Expected track from $url to have points", track.points.isNotEmpty())
        }
    }
}
