package no.synth.where.integration

import no.synth.where.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
        assumeTrue("Set STRAVA_URL in local.properties to a public Strava activity/route URL", stravaUrl.isNotEmpty())
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

    /** Shows exactly what the JVM OkHttp client gets from Strava — useful for diagnosing CDN routing. */
    @Test
    fun diagnose_stravaHttpResponse() = runBlocking {
        val client = makeClient()
        val ua = "Mozilla/5.0 (compatible; Where/1.0)"

        val warmUp = client.get("https://www.strava.com") {
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "en-US,en;q=0.9")
            header("User-Agent", ua)
        }
        println("Warm-up status: ${warmUp.status}")

        val response = client.get(stravaUrl) {
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "en-US,en;q=0.9")
            header("User-Agent", ua)
        }
        println("Activity status: ${response.status}")
        val html = response.bodyAsText()
        println("HTML size: ${html.length}")
        println("HTML preview: ${html.take(300)}")
        println("Has __NEXT_DATA__: ${html.contains("__NEXT_DATA__")}")
        println("Has polyline: ${html.contains("polyline")}")
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
