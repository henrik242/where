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
 * Integration test that makes real HTTP requests to Komoot.
 * Run with: ../gradlew integrationTest
 * (KOMOOT_URL is read from local.properties)
 */
class KomootFetchTest {

    private val komootUrl: String = System.getenv("KOMOOT_URL") ?: ""

    @Before
    fun setUp() {
        assumeTrue("Set KOMOOT_URL in local.properties to a public Komoot tour URL", komootUrl.isNotEmpty())
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

    /** Shows exactly what the JVM OkHttp client gets from Komoot — JSON vs HTML response. */
    @Test
    fun diagnose_komootHttpResponse() = runBlocking {
        val client = makeClient()

        val jsonResponse = client.get(komootUrl) {
            header("Accept", "application/hal+json,application/json")
            header("Accept-Language", "en-US,en;q=0.9")
            header("User-Agent", "Mozilla/5.0 (compatible; Where/1.0)")
        }
        val jsonBody = jsonResponse.bodyAsText()
        println("JSON endpoint status: ${jsonResponse.status}")
        println("Body size: ${jsonBody.length}")
        println("Body preview: ${jsonBody.take(300)}")
        println("Has coordinates/items: ${jsonBody.contains("\"items\"")}")
        println("Has lat: ${jsonBody.contains("\"lat\"")}")
        println("Has _embedded: ${jsonBody.contains("_embedded")}")
    }

    @Test
    fun importTour_getsTrackWithPoints() = runBlocking {
        val importer = KomootImporter(makeClient())
        val track = importer.importFromUrl(komootUrl, addElevation = false)
        println("Track: name=${track?.name}, points=${track?.points?.size}")
        checkNotNull(track) { "Expected to import track but got null" }
        assertTrue("Expected track to have points", track.points.isNotEmpty())
    }
}
