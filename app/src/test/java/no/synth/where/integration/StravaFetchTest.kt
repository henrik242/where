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
 * Integration test that makes real HTTP requests to Strava.
 * Run with: ../gradlew integrationTest
 * (STRAVA_URL is read from local.properties)
 */
class StravaFetchTest {

    private val stravaUrl: String = System.getenv("STRAVA_URL") ?: ""

    @Before
    fun setUp() {
        require(stravaUrl.isNotEmpty()) { "STRAVA_URL must be set in local.properties or environment" }
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
        val importer = StravaImporter(makeClient())
        val track = importer.importFromUrl(stravaUrl, addElevation = false)
        println("Track: name=${track?.name}, points=${track?.points?.size}")
        checkNotNull(track) { "Expected to import track but got null" }
        assertTrue("Expected track to have points", track.points.isNotEmpty())
    }
}
