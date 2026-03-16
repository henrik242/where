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
    fun reverseGeocode_combinesRoadAndCity() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"address":{"road":"Karl Johans gate","city":"Oslo"},"display_name":"Karl Johans gate, Oslo"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.9139, 10.7522))
        assertEquals("Karl Johans gate, Oslo", result)
    }

    @Test
    fun reverseGeocode_fallsToVillage_whenNoRoad() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("search")) {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(
                    content = """{"address":{"village":"Rjukan"},"display_name":"Rjukan, Tinn"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59))
        assertEquals("Rjukan", result)
    }

    @Test
    fun reverseGeocode_fallsToCity_whenNoRoadOrVillage() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("search")) {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(
                    content = """{"address":{"city":"Bergen"},"display_name":"Bergen, Vestland"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(60.39, 5.32))
        assertEquals("Bergen", result)
    }

    @Test
    fun reverseGeocode_fallsToDisplayName_whenNoAddress() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("search")) {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(
                    content = """{"address":{},"display_name":"Somewhere, Norway"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
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
    fun reverseGeocode_combinesHamletAndVillage() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"address":{"hamlet":"Skinnarbu","village":"Rjukan","county":"Vestfold og Telemark"},"display_name":"Skinnarbu, Rjukan"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59))
        assertEquals("Skinnarbu, Rjukan", result)
    }

    @Test
    fun reverseGeocode_combinesLocalityAndMunicipality_whenNoPeakNearby() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("search")) {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(
                    content = """{"address":{"locality":"Kråfjellet","municipality":"Luster","county":"Vestland"},"display_name":"Kråfjellet, Luster, Vestland, Norge"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(61.69, 7.21))
        assertEquals("Kråfjellet, Luster", result)
    }

    @Test
    fun reverseGeocode_prefersPeakOverLocality() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("search")) {
                respond(
                    content = """[{"name":"Dueskardhøgdi"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"address":{"locality":"Galdarabb","municipality":"Sogndal","county":"Vestland"},"display_name":"Galdarabb, Sogndal, Vestland, Norge"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(61.15, 7.036))
        assertEquals("Dueskardhøgdi, Sogndal", result)
    }

    @Test
    fun reverseGeocode_returnsMunicipality_whenOnlyBroadAndNoPeak() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("search")) {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(
                    content = """{"address":{"municipality":"Tinn","county":"Vestfold og Telemark"},"display_name":"Tinn, Vestfold og Telemark"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59))
        assertEquals("Tinn", result)
    }

    @Test
    fun reverseGeocode_findsNearbyPeak_whenOnlyBroadAvailable() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("search")) {
                respond(
                    content = """[{"name":"Dueskardhøgdi","lat":"61.15","lon":"7.036","class":"natural","type":"peak"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"address":{"municipality":"Sogndal","county":"Vestland"},"display_name":"Sogndal, Vestland, Norge"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(61.15, 7.036))
        assertEquals("Dueskardhøgdi, Sogndal", result)
    }

    @Test
    fun reverseGeocode_returnsRoadOnly_whenNoBroadContext() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine {
            respond(
                content = """{"address":{"road":"Fv40"},"display_name":"Fv40, Norway"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.88, 8.59))
        assertEquals("Fv40", result)
    }

    @Test
    fun reverseGeocode_prefersPoiName_overRoad() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.parameters.contains("layer", "poi,natural")) {
                respond(
                    content = """{"osm_type":"way","class":"historic","type":"croft","name":"Skansebakken","address":{"historic":"Skansebakken","road":"Ospeskogveien","city":"Oslo","municipality":"Oslo"},"display_name":"Skansebakken, Ospeskogveien, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"address":{"road":"Ospeskogveien","city":"Oslo","municipality":"Oslo"},"display_name":"Ospeskogveien, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(60.018, 10.583))
        assertEquals("Skansebakken, Oslo", result)
    }

    @Test
    fun reverseGeocode_fallsBackToAddress_whenPoiHasNoName() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.parameters.contains("layer", "poi,natural")) {
                respond(
                    content = """{"osm_type":"way","name":"","address":{}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"address":{"road":"Ospeskogveien","city":"Oslo","municipality":"Oslo"},"display_name":"Ospeskogveien, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(60.018, 10.583))
        assertEquals("Ospeskogveien, Oslo", result)
    }

    @Test
    fun reverseGeocode_findsBuilding_whenPoiIsNode() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.parameters.contains("layer", "poi,natural")) {
                respond(
                    content = """{"osm_type":"node","class":"amenity","name":"Kranen","address":{"amenity":"Kranen","road":"Operatunnelen","city":"Oslo","municipality":"Oslo"},"display_name":"Kranen, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (request.url.host == "overpass-api.de") {
                respond(
                    content = """{"elements":[{"type":"way","id":545260792,"tags":{"name":"Munchmuseet","building":"civic","tourism":"museum"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"address":{"road":"Operatunnelen","city":"Oslo","municipality":"Oslo"},"display_name":"Operatunnelen, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.9056, 10.7551))
        assertEquals("Munchmuseet, Oslo", result)
    }

    @Test
    fun reverseGeocode_fallsBackToAddress_whenNodePoiAndNoBuilding() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.parameters.contains("layer", "poi,natural")) {
                respond(
                    content = """{"osm_type":"node","class":"amenity","name":"Some bench","address":{"road":"Storgata","city":"Oslo","municipality":"Oslo"},"display_name":"Storgata, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (request.url.host == "overpass-api.de") {
                respond(
                    content = """{"elements":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"address":{"road":"Storgata","city":"Oslo","municipality":"Oslo"},"display_name":"Storgata, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.914, 10.752))
        assertEquals("Storgata, Oslo", result)
    }

    @Test
    fun reverseGeocode_returnsLakeName_fromNaturalLayer() = runBlocking {
        originalClient = GeocodingHelper.client
        GeocodingHelper.client = HttpClient(MockEngine { request ->
            if (request.url.parameters.contains("layer", "poi,natural")) {
                respond(
                    content = """{"osm_type":"relation","class":"natural","type":"water","name":"Maridalsvannet","address":{"water":"Maridalsvannet","city":"Oslo","municipality":"Oslo"},"display_name":"Maridalsvannet, Oslo, Norge"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"address":{"road":"Maridalsveien","city":"Oslo","municipality":"Oslo"},"display_name":"Maridalsveien, Oslo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val result = GeocodingHelper.reverseGeocode(LatLng(59.99, 10.77))
        assertEquals("Maridalsvannet, Oslo", result)
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
