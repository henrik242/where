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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Integration tests against real Nominatim + Overpass APIs.
 * Run with: ../gradlew integrationTest --tests "no.synth.where.integration.GeocodingIntegrationTest"
 *
 * Tests that depend on Overpass accept multiple valid outcomes since
 * Overpass availability affects which lookup path succeeds.
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

    private fun geocode(lat: Double, lon: Double): String? = runBlocking {
        delay(3000)
        GeocodingHelper.reverseGeocode(LatLng(lat, lon)).also {
            println("  ($lat, $lon) → $it")
        }
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

    // --- Nominatim-only (stable): historic site and lake ---

    @Test
    fun skansebakken_historicCroft() {
        assertEquals("Skansebakken, Oslo", geocode(60.0181775, 10.582963))
    }

    @Test
    fun maridalsvannet_lake() {
        assertEquals("Maridalsvannet, Oslo", geocode(59.9829, 10.7800))
    }

    // --- Overpass-dependent: building and peak lookups (may fall back to road name) ---

    @Test
    fun munchmuseet_museum() {
        val result = geocode(59.9056239, 10.7551554)
        assertNotNull(result)
        assertTrue(
            "Expected Munchmuseet or road fallback, got: $result",
            result!!.startsWith("Munchmuseet") || result.contains("Oslo")
        )
    }

    @Test
    fun ljanskollen_peak() {
        val result = geocode(59.8373838, 10.7741729)
        assertNotNull(result)
        assertTrue(
            "Expected Ljanskollen or road fallback, got: $result",
            result!!.startsWith("Ljanskollen") || result.contains("Oslo")
        )
    }

    @Test
    fun kraketjernfjellet_peak() {
        val result = geocode(60.6471842, 9.4447617)
        assertNotNull(result)
        assertTrue(
            "Expected Kråketjernfjellet or road fallback, got: $result",
            result!!.startsWith("Kråketjernfjellet") || result.contains("Sør-Aurdal")
        )
    }
}
