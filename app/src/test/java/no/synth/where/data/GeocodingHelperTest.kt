package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import no.synth.where.data.geo.LatLng
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GeocodingHelperTest {

    private lateinit var originalClient: HttpClient

    @After
    fun restore() {
        if (::originalClient.isInitialized) {
            GeocodingHelper.client = originalClient
        }
    }

    @Test
    fun reverseGeocode_returnsRoad() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"address":{"road":"Karl Johans gate","city":"Oslo"},"display_name":"Karl Johans gate, Oslo"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.9139, 10.7522))
        assertEquals("Karl Johans gate", result)
    }

    @Test
    fun reverseGeocode_fallsToVillage_whenNoRoad() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"address":{"village":"Rjukan"},"display_name":"Rjukan, Tinn"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59))
        assertEquals("Rjukan", result)
    }

    @Test
    fun reverseGeocode_fallsToCity_whenNoRoadOrVillage() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"address":{"city":"Bergen"},"display_name":"Bergen, Vestland"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(60.39, 5.32))
        assertEquals("Bergen", result)
    }

    @Test
    fun reverseGeocode_fallsToDisplayName_whenNoAddress() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"address":{},"display_name":"Somewhere, Norway"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(62.0, 10.0))
        assertEquals("Somewhere", result)
    }

    @Test
    fun reverseGeocode_returnsNull_onHttpError() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = "Server Error",
                status = HttpStatusCode.InternalServerError
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.9, 10.7))
        assertNull(result)
    }

    @Test
    fun reverseGeocode_returnsNull_onMissingAddress() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"display_name":"Middle of nowhere"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(70.0, 25.0))
        assertNull(result)
    }
}
