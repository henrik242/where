package no.synth.where.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.geo.LatLng
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Integration tests against real Nominatim + Overpass APIs.
 * Run with: ../gradlew integrationTest --tests "no.synth.where.integration.GeocodingIntegrationTest"
 *
 * Each probe below is grouped by the lookup path it exercises. Every probe asserts its exact
 * expected name; the ones that need Overpass retry first, since the public mirrors drop a large
 * share of requests and a single miss would say nothing about our own code.
 */
class GeocodingIntegrationTest {

    private lateinit var originalClient: HttpClient

    @Before
    fun setUp() {
        IntegrationTestSupport.plantLogger()
        runBlocking { GeocodingHelper.clearCaches() }
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(45, TimeUnit.SECONDS)
                }
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, "Where-IntegrationTest (https://github.com/henrik242/where)")
            }
        }
    }

    @After
    fun restore() {
        GeocodingHelper.client = originalClient
    }

    private fun geocode(lat: Double, lon: Double): String? = runBlocking {
        delay(3000)
        GeocodingHelper.reverseGeocode(LatLng(lat, lon)).also {
            println("  ($lat, $lon) → $it")
        }
    }

    /**
     * For probes that need Overpass: public mirrors reject a large share of requests under load,
     * so one miss proves nothing. Retry until [expected] shows up, then report whatever came last.
     */
    private fun geocodeExpecting(expected: String, lat: Double, lon: Double): String? {
        var result: String? = null
        repeat(4) { attempt ->
            if (attempt > 0) runBlocking {
                GeocodingHelper.clearCaches()
                delay(5000)
            }
            result = geocode(lat, lon)
            if (result == expected) return result
        }
        return result
    }

    // --- Diagnostic: prints results without asserting ---

    @Test
    fun printResults() = runBlocking {
        data class Probe(val name: String, val lat: Double, val lon: Double)
        listOf(
            Probe("Skansebakken", 60.0181775, 10.582963),
            Probe("Maridalsvannet", 59.9829, 10.7800),
            Probe("Munchmuseet", 59.9056239, 10.7551554),
            Probe("Ljanskollen", 59.8373838, 10.7741729),
            Probe("Kråketjernfjellet", 60.6471842, 9.4447617),
        ).forEach { p ->
            delay(3000)
            val result = GeocodingHelper.reverseGeocode(LatLng(p.lat, p.lon))
            println("${p.name}: (${p.lat}, ${p.lon}) → $result")
        }
    }

    // --- Nominatim answers directly: the reverse lookup already returns a landmark ---

    @Test
    fun skansebakken_historicCroft() {
        assertEquals("Skansebakken, Oslo", geocode(60.0181775, 10.582963))
    }

    // --- Nominatim finds a non-landmark POI, the nearby-peak search (also Nominatim) rescues it ---

    @Test
    fun ljanskollen_peak() {
        assertEquals("Ljanskollen, Oslo", geocode(59.8373838, 10.7741729))
    }

    @Test
    fun kraketjernfjellet_peak() {
        assertEquals("Kråketjernfjellet, Sør-Aurdal", geocode(60.6471842, 9.4447617))
    }

    // --- Overpass supplies the answer: the enclosing building around a POI node ---

    // Nominatim resolves this point to a defibrillator node; the museum is the building it hangs on.
    @Test
    fun munchmuseet_building() {
        val expected = "Munchmuseet, Oslo"
        assertEquals(expected, geocodeExpecting(expected, 59.9056239, 10.7551554))
    }

    // --- Overpass supplies the answer: the enclosing water body around a cape ---

    // Nominatim resolves this point to the cape "Nestangen"; only Overpass knows the lake.
    @Test
    fun maridalsvannet_lake() {
        val expected = "Maridalsvannet, Oslo"
        assertEquals(expected, geocodeExpecting(expected, 59.9829, 10.7800))
    }
}
