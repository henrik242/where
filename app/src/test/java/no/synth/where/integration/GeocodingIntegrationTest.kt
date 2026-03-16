package no.synth.where.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.geo.LatLng
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Integration tests that hit real Nominatim + Overpass APIs to verify
 * reverse geocoding returns the expected place names.
 *
 * Run with: ../gradlew integrationTest --tests "no.synth.where.integration.GeocodingIntegrationTest"
 */
class GeocodingIntegrationTest {

    private lateinit var originalClient: HttpClient

    @Before
    fun setUp() {
        if (Timber.treeCount == 0) {
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    println("[$tag] $message")
                    t?.printStackTrace()
                }
            })
        }
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                }
            }
        }
    }

    @After
    fun restore() {
        GeocodingHelper.client = originalClient
    }

    private fun assertGeocode(lat: Double, lon: Double, expected: String) = runBlocking {
        delay(1100) // respect Nominatim rate limit (1 req/sec)
        val result = GeocodingHelper.reverseGeocode(LatLng(lat, lon))
        println("  ($lat, $lon) → $result")
        assertNotNull("Expected '$expected' but got null", result)
        assertEquals(expected, result)
    }

    // --- Diagnostic: prints results without asserting. Useful for exploring new coords. ---

    @Test
    fun printResults() = runBlocking {
        data class Probe(val name: String, val lat: Double, val lon: Double)
        val probes = listOf(
            Probe("Skansebakken", 60.0181775, 10.582963),
            Probe("Maridalsvannet", 59.9829, 10.7800),
            Probe("Munchmuseet", 59.9056239, 10.7551554),
            Probe("Ljanskollen", 59.8373838, 10.7741729),
            Probe("Kråketjernfjellet", 60.6471842, 9.4447617),
            Probe("Karl Johans gate", 59.9138, 10.7400),
        )
        for (p in probes) {
            delay(1100)
            val result = GeocodingHelper.reverseGeocode(LatLng(p.lat, p.lon))
            println("${p.name}: (${p.lat}, ${p.lon}) → $result")
        }
    }

    // --- Historic site (way) — should prefer POI name over road ---

    @Test
    fun skansebakken_historicCroft() {
        // https://nominatim.openstreetmap.org/ui/details.html?osmtype=W&osmid=852624525&class=historic
        assertGeocode(60.0181775, 10.582963, "Skansebakken, Oslo")
    }

    // --- Lake (relation) — should return water body name ---

    @Test
    fun maridalsvannet_lake() {
        assertGeocode(59.9829, 10.7800, "Maridalsvannet, Oslo")
    }

    // --- Museum building — Nominatim returns bar node, Overpass finds building ---

    @Test
    fun munchmuseet_museum() {
        assertGeocode(59.9056239, 10.7551554, "Munchmuseet, Oslo")
    }

    // --- Peak near a road — peak should take priority ---

    @Test
    fun ljanskollen_peak() {
        assertGeocode(59.8373838, 10.7741729, "Ljanskollen, Oslo")
    }

    @Test
    fun kraketjernfjellet_peak() {
        assertGeocode(60.6471842, 9.4447617, "Kråketjernfjellet, Sør-Aurdal")
    }

    // --- Normal city street — should return road + city ---

    @Test
    fun karlJohansGate_cityStreet() {
        assertGeocode(59.9138, 10.7400, "Karl Johans gate, Oslo")
    }
}
