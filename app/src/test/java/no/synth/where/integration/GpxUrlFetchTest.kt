package no.synth.where.integration

import no.synth.where.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test that fetches a real GPX file from a URL.
 * Run with: ../gradlew integrationTest
 */
class GpxUrlFetchTest {

    private val gpxUrls = listOf(
        "https://raw.githubusercontent.com/henrik242/where/bfc3ed08e6bfb8eba43f8e07511b86a539a3b364/shared/src/commonTest/resources/test_track.gpx",
    )

    @Before
    fun setUp() = IntegrationTestSupport.plantLogger()

    @Test
    fun importGpxUrl_getsTrackWithPoints() = runBlocking {
        val importer = GpxUrlImporter(IntegrationTestSupport.makeClient())
        for (url in gpxUrls) {
            val track = importer.importFromUrl(url, addElevation = false)
            println("Track ($url): name=${track?.name}, points=${track?.points?.size}")
            checkNotNull(track) { "Expected to import track from $url but got null" }
            assertTrue("Expected track from $url to have points", track.points.isNotEmpty())
        }
    }
}
