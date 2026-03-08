package no.synth.where.integration

import no.synth.where.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Integration test that makes real HTTP requests to Garmin Connect.
 * Run with: ../gradlew integrationTest
 * (GARMIN_URL is read from local.properties)
 */
class GarminFetchTest {

    private val garminUrls: List<String> = (System.getenv("GARMIN_URL") ?: "")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    @Before
    fun setUp() {
        require(garminUrls.isNotEmpty()) { "GARMIN_URL must be set in local.properties or environment" }
        if (Timber.treeCount == 0) {
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    println("[$tag] $message")
                    t?.printStackTrace()
                }
            })
        }
    }

    private fun makeClient() = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun importActivity_getsTrackWithPoints() = runBlocking {
        val importer = GarminImporter(makeClient())
        for (url in garminUrls) {
            val track = importer.importFromUrl(url, addElevation = false)
            println("Track ($url): name=${track?.name}, points=${track?.points?.size}")
            checkNotNull(track) { "Expected to import track from $url but got null" }
            assertTrue("Expected track from $url to have points", track.points.isNotEmpty())
        }
    }
}
