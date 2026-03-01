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
 * Integration test that makes real HTTP requests to Garmin Connect.
 * Run with: ../gradlew integrationTest
 * (GARMIN_URL is read from local.properties)
 */
class GarminFetchTest {

    private val garminUrl: String = System.getenv("GARMIN_URL") ?: ""

    @Before
    fun setUp() {
        assumeTrue("Set GARMIN_URL in local.properties to a public Garmin activity/course URL", garminUrl.isNotEmpty())
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

    /** Shows exactly what the JVM OkHttp client gets from Garmin Connect. */
    @Test
    fun diagnose_garminHttpResponse() = runBlocking {
        val ref = GarminImporter.parseGarminUrl(garminUrl) ?: error("Could not parse GARMIN_URL: $garminUrl")
        val client = makeClient()

        val apiPath = if (ref.type == "course") "course-service/course" else "activity-service/activity"
        val suffix = if (ref.type == "course") "" else "/details?maxPolylineSize=4000&maxChartSize=100"
        val apiUrl = "https://connect.garmin.com/proxy/$apiPath/${ref.id}$suffix"

        val response = client.get(apiUrl) {
            header("Accept", "application/json")
            header("User-Agent", "Mozilla/5.0 (compatible; Where/1.0)")
            header("Referer", garminUrl)
            header("NK", "NT")
            header("DI-Backend", "connectapi.garmin.com")
        }
        val body = response.bodyAsText()
        println("API status: ${response.status}")
        println("Body size: ${body.length}")
        println("Body preview: ${body.take(300)}")
        println("Has polyline: ${body.contains("polyline")}")
        println("Has geoPolylineDTO: ${body.contains("geoPolylineDTO")}")
    }

    @Test
    fun importActivity_getsTrackWithPoints() = runBlocking {
        val importer = GarminImporter(makeClient())
        val track = importer.importFromUrl(garminUrl, addElevation = false)
        println("Track: name=${track?.name}, points=${track?.points?.size}")
        checkNotNull(track) { "Expected to import track but got null" }
        assertTrue("Expected track to have points", track.points.isNotEmpty())
    }
}
