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
 * Integration test that makes real HTTP requests to Komoot.
 * Run with: ../gradlew integrationTest
 * (KOMOOT_URL is read from local.properties)
 */
class KomootFetchTest {

    private val komootUrls: List<String> = (System.getenv("KOMOOT_URL") ?: "")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    @Before
    fun setUp() {
        require(komootUrls.isNotEmpty()) { "KOMOOT_URL must be set in local.properties or environment" }
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
    fun importTour_getsTrackWithPoints() = runBlocking {
        val importer = KomootImporter(makeClient())
        for (url in komootUrls) {
            val track = importer.importFromUrl(url, addElevation = false)
            println("Track ($url): name=${track?.name}, points=${track?.points?.size}")
            checkNotNull(track) { "Expected to import track from $url but got null" }
            assertTrue("Expected track from $url to have points", track.points.isNotEmpty())
        }
    }
}
