package no.synth.where.integration

import no.synth.where.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test that makes real HTTP requests to Garmin Connect.
 * Run with: ../gradlew integrationTest
 */
class GarminFetchTest {

    private val garminUrls = listOf(
        "https://connect.garmin.com/app/course/421983675",
        "https://connect.garmin.com/app/activity/22081967173",
    )

    @Before
    fun setUp() = IntegrationTestSupport.plantLogger()

    @Test
    fun importActivity_getsTrackWithPoints() = runBlocking {
        val importer = GarminImporter(IntegrationTestSupport.makeClient())
        for (url in garminUrls) {
            val track = importer.importFromUrl(url, addElevation = false)
            println("Track ($url): name=${track?.name}, points=${track?.points?.size}")
            checkNotNull(track) { "Expected to import track from $url but got null" }
            assertTrue("Expected track from $url to have points", track.points.isNotEmpty())
        }
    }
}
