package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PlaceSearchClientTest {

    private lateinit var originalClient: HttpClient

    @After
    fun restore() {
        if (::originalClient.isInitialized) {
            PlaceSearchClient.client = originalClient
        }
    }

    @Test
    fun search_parsesGeonorgeResponse() = runBlocking {
        originalClient = PlaceSearchClient.client
        PlaceSearchClient.client = HttpClient(MockEngine {
            respond(
                content = """{
                    "navn": [
                        {
                            "stedsnavn": [{"skrivemåte": "Galdhøpiggen"}],
                            "navneobjekttype": "Fjell",
                            "kommuner": [{"kommunenavn": "Lom"}],
                            "representasjonspunkt": {"øst": 8.3124, "nord": 61.6364}
                        }
                    ]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val results = PlaceSearchClient.search("Galdhøpiggen")
        assertEquals(1, results.size)
        assertEquals("Galdhøpiggen", results[0].name)
        assertEquals("Fjell", results[0].type)
        assertEquals("Lom", results[0].municipality)
        assertEquals(61.6364, results[0].latLng.latitude, 0.001)
        assertEquals(8.3124, results[0].latLng.longitude, 0.001)
    }

    @Test
    fun search_returnsMultipleResults() = runBlocking {
        originalClient = PlaceSearchClient.client
        PlaceSearchClient.client = HttpClient(MockEngine {
            respond(
                content = """{
                    "navn": [
                        {
                            "stedsnavn": [{"skrivemåte": "Oslo"}],
                            "navneobjekttype": "By",
                            "kommuner": [{"kommunenavn": "Oslo"}],
                            "representasjonspunkt": {"øst": 10.7522, "nord": 59.9139}
                        },
                        {
                            "stedsnavn": [{"skrivemåte": "Oslofjorden"}],
                            "navneobjekttype": "Fjord",
                            "kommuner": [{"kommunenavn": "Oslo"}],
                            "representasjonspunkt": {"øst": 10.6, "nord": 59.7}
                        }
                    ]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val results = PlaceSearchClient.search("Oslo")
        assertEquals(2, results.size)
        assertEquals("Oslo", results[0].name)
        assertEquals("Oslofjorden", results[1].name)
    }

    @Test
    fun search_returnsEmpty_onHttpError() = runBlocking {
        originalClient = PlaceSearchClient.client
        PlaceSearchClient.client = HttpClient(MockEngine {
            respond(
                content = "Server Error",
                status = HttpStatusCode.InternalServerError
            )
        })
        val results = PlaceSearchClient.search("test")
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_returnsEmpty_onEmptyResults() = runBlocking {
        originalClient = PlaceSearchClient.client
        PlaceSearchClient.client = HttpClient(MockEngine {
            respond(
                content = """{"navn": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val results = PlaceSearchClient.search("xyznonexistent")
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_skipsEntries_withBlankName() = runBlocking {
        originalClient = PlaceSearchClient.client
        PlaceSearchClient.client = HttpClient(MockEngine {
            respond(
                content = """{
                    "navn": [
                        {
                            "stedsnavn": [{"skrivemåte": ""}],
                            "navneobjekttype": "By",
                            "kommuner": [{"kommunenavn": "Oslo"}],
                            "representasjonspunkt": {"øst": 10.7, "nord": 59.9}
                        },
                        {
                            "stedsnavn": [{"skrivemåte": "Bergen"}],
                            "navneobjekttype": "By",
                            "kommuner": [{"kommunenavn": "Bergen"}],
                            "representasjonspunkt": {"øst": 5.32, "nord": 60.39}
                        }
                    ]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val results = PlaceSearchClient.search("test")
        assertEquals(1, results.size)
        assertEquals("Bergen", results[0].name)
    }

    @Test
    fun search_skipsEntries_withMissingCoordinates() = runBlocking {
        originalClient = PlaceSearchClient.client
        PlaceSearchClient.client = HttpClient(MockEngine {
            respond(
                content = """{
                    "navn": [
                        {
                            "stedsnavn": [{"skrivemåte": "NoCoords"}],
                            "navneobjekttype": "Sted",
                            "kommuner": [{"kommunenavn": "Ukjent"}]
                        }
                    ]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val results = PlaceSearchClient.search("test")
        assertTrue(results.isEmpty())
    }
}
