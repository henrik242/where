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

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
private const val EMPTY_OVERPASS = """{"elements":[]}"""

class GeocodingHelperTest {

    private lateinit var originalClient: HttpClient

    @After
    fun restore() {
        if (::originalClient.isInitialized) {
            GeocodingHelper.client = originalClient
        }
    }

    private fun mockClient(handler: (io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.http.Url) -> io.ktor.client.request.HttpResponseData)): HttpClient {
        originalClient = GeocodingHelper.client
        return HttpClient(MockEngine { request -> handler(request.url) }).also {
            GeocodingHelper.client = it
        }
    }

    private fun isOverpass(url: io.ktor.http.Url) = url.host == "overpass-api.de"
    private fun isPoi(url: io.ktor.http.Url) = url.parameters.contains("layer", "poi,natural")

    @Test
    fun reverseGeocode_combinesRoadAndCity() = runBlocking {
        mockClient { respond("""{"address":{"road":"Karl Johans gate","city":"Oslo"}}""", HttpStatusCode.OK, JSON_HEADERS) }
        assertEquals("Karl Johans gate, Oslo", GeocodingHelper.reverseGeocode(LatLng(59.9139, 10.7522)))
    }

    @Test
    fun reverseGeocode_fallsToVillage_whenNoRoad() = runBlocking {
        mockClient { url ->
            if (isOverpass(url)) respond(EMPTY_OVERPASS, HttpStatusCode.OK, JSON_HEADERS)
            else respond("""{"address":{"village":"Rjukan"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Rjukan", GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59)))
    }

    @Test
    fun reverseGeocode_fallsToCity_whenNoRoadOrVillage() = runBlocking {
        mockClient { url ->
            if (isOverpass(url)) respond(EMPTY_OVERPASS, HttpStatusCode.OK, JSON_HEADERS)
            else respond("""{"address":{"city":"Bergen"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Bergen", GeocodingHelper.reverseGeocode(LatLng(60.39, 5.32)))
    }

    @Test
    fun reverseGeocode_fallsToDisplayName_whenEmptyAddress() = runBlocking {
        mockClient { url ->
            if (isOverpass(url)) respond(EMPTY_OVERPASS, HttpStatusCode.OK, JSON_HEADERS)
            else respond("""{"address":{},"display_name":"Somewhere, Norway"}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Somewhere", GeocodingHelper.reverseGeocode(LatLng(62.0, 10.0)))
    }

    @Test
    fun reverseGeocode_returnsNull_onHttpError() = runBlocking {
        mockClient { respond("Server Error", HttpStatusCode.InternalServerError) }
        assertNull(GeocodingHelper.reverseGeocode(LatLng(59.9, 10.7)))
    }

    @Test
    fun reverseGeocode_combinesHamletAndVillage() = runBlocking {
        mockClient { respond("""{"address":{"hamlet":"Skinnarbu","village":"Rjukan"}}""", HttpStatusCode.OK, JSON_HEADERS) }
        assertEquals("Skinnarbu, Rjukan", GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59)))
    }

    @Test
    fun reverseGeocode_combinesLocalityAndMunicipality_whenNoPeak() = runBlocking {
        mockClient { url ->
            if (isOverpass(url)) respond(EMPTY_OVERPASS, HttpStatusCode.OK, JSON_HEADERS)
            else respond("""{"address":{"locality":"Kråfjellet","municipality":"Luster"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Kråfjellet, Luster", GeocodingHelper.reverseGeocode(LatLng(61.69, 7.21)))
    }

    @Test
    fun reverseGeocode_prefersPeakOverLocality() = runBlocking {
        mockClient { url ->
            if (isOverpass(url)) respond(
                """{"elements":[{"type":"node","tags":{"name":"Dueskardhøgdi"}}]}""",
                HttpStatusCode.OK, JSON_HEADERS
            )
            else respond("""{"address":{"locality":"Galdarabb","municipality":"Sogndal"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Dueskardhøgdi, Sogndal", GeocodingHelper.reverseGeocode(LatLng(61.15, 7.036)))
    }

    @Test
    fun reverseGeocode_returnsMunicipality_whenNoPeak() = runBlocking {
        mockClient { url ->
            if (isOverpass(url)) respond(EMPTY_OVERPASS, HttpStatusCode.OK, JSON_HEADERS)
            else respond("""{"address":{"municipality":"Tinn"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Tinn", GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59)))
    }

    @Test
    fun reverseGeocode_findsNearbyPeak() = runBlocking {
        mockClient { url ->
            if (isOverpass(url)) respond(
                """{"elements":[{"type":"node","tags":{"name":"Dueskardhøgdi"}}]}""",
                HttpStatusCode.OK, JSON_HEADERS
            )
            else respond("""{"address":{"municipality":"Sogndal"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Dueskardhøgdi, Sogndal", GeocodingHelper.reverseGeocode(LatLng(61.15, 7.036)))
    }

    @Test
    fun reverseGeocode_returnsRoadOnly_whenNoBroadContext() = runBlocking {
        mockClient { respond("""{"address":{"road":"Fv40"}}""", HttpStatusCode.OK, JSON_HEADERS) }
        assertEquals("Fv40", GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59)))
    }

    @Test
    fun reverseGeocode_prefersLandmark_overRoad() = runBlocking {
        mockClient { url ->
            if (isPoi(url)) respond(
                """{"class":"historic","type":"croft","name":"Skansebakken","address":{"city":"Oslo","municipality":"Oslo"}}""",
                HttpStatusCode.OK, JSON_HEADERS
            )
            else respond("""{"address":{"road":"Ospeskogveien","city":"Oslo","municipality":"Oslo"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Skansebakken, Oslo", GeocodingHelper.reverseGeocode(LatLng(60.018, 10.583)))
    }

    @Test
    fun reverseGeocode_fallsBackToAddress_whenPoiHasNoName() = runBlocking {
        mockClient { url ->
            if (isPoi(url)) respond("""{"name":"","address":{}}""", HttpStatusCode.OK, JSON_HEADERS)
            else respond("""{"address":{"road":"Ospeskogveien","city":"Oslo","municipality":"Oslo"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Ospeskogveien, Oslo", GeocodingHelper.reverseGeocode(LatLng(60.018, 10.583)))
    }

    @Test
    fun reverseGeocode_findsBuilding_whenPoiIsAmenity() = runBlocking {
        mockClient { url ->
            if (isPoi(url)) respond(
                """{"class":"amenity","name":"Kranen","address":{"city":"Oslo","municipality":"Oslo"}}""",
                HttpStatusCode.OK, JSON_HEADERS
            )
            else if (isOverpass(url)) respond(
                """{"elements":[{"type":"way","tags":{"name":"Munchmuseet"}}]}""",
                HttpStatusCode.OK, JSON_HEADERS
            )
            else respond("""{"address":{"road":"Operatunnelen","city":"Oslo","municipality":"Oslo"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Munchmuseet, Oslo", GeocodingHelper.reverseGeocode(LatLng(59.9056, 10.7551)))
    }

    @Test
    fun reverseGeocode_fallsBackToAddress_whenAmenityAndNoBuilding() = runBlocking {
        mockClient { url ->
            if (isPoi(url)) respond(
                """{"class":"amenity","name":"Some bench","address":{"city":"Oslo","municipality":"Oslo"}}""",
                HttpStatusCode.OK, JSON_HEADERS
            )
            else if (isOverpass(url)) respond(EMPTY_OVERPASS, HttpStatusCode.OK, JSON_HEADERS)
            else respond("""{"address":{"road":"Storgata","city":"Oslo","municipality":"Oslo"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Storgata, Oslo", GeocodingHelper.reverseGeocode(LatLng(59.914, 10.752)))
    }

    @Test
    fun reverseGeocode_returnsLakeName() = runBlocking {
        mockClient { url ->
            if (isPoi(url)) respond(
                """{"class":"natural","type":"water","name":"Maridalsvannet","address":{"city":"Oslo","municipality":"Oslo"}}""",
                HttpStatusCode.OK, JSON_HEADERS
            )
            else respond("""{"address":{"road":"Maridalsveien","city":"Oslo","municipality":"Oslo"}}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertEquals("Maridalsvannet, Oslo", GeocodingHelper.reverseGeocode(LatLng(59.99, 10.77)))
    }

    @Test
    fun reverseGeocode_returnsNull_onMissingAddress() = runBlocking {
        mockClient { respond("""{"display_name":"Middle of nowhere"}""", HttpStatusCode.OK, JSON_HEADERS) }
        assertNull(GeocodingHelper.reverseGeocode(LatLng(70.0, 25.0)))
    }
}
