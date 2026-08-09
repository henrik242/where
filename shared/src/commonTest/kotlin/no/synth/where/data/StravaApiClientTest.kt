package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StravaApiClientTest {

    @Test
    fun parseRoutes_parsesSummaryFields() {
        val json = """
            [
              {
                "id": 743064,
                "id_str": "743064",
                "name": "Woodland Trail",
                "distance": 17547.5,
                "elevation_gain": 300.0,
                "type": 1,
                "sub_type": 1,
                "private": false,
                "starred": true,
                "timestamp": 1387834975,
                "map": { "id": "r743064", "summary_polyline": "abc123", "polyline": null }
              }
            ]
        """.trimIndent()

        val routes = StravaApiClient.parseRoutes(json)
        assertEquals(1, routes.size)
        val r = routes[0]
        assertEquals(743064L, r.id)
        assertEquals("Woodland Trail", r.name)
        assertEquals(17547.5, r.distanceMeters, 0.001)
        assertEquals(300.0, r.elevationGainMeters, 0.001)
        assertTrue(r.starred)
    }

    @Test
    fun parseRoutes_handlesLargeIdsAndMissingFields() {
        val json = """
            [
              { "id": 22222222222, "distance": 50000.0, "type": 2 }
            ]
        """.trimIndent()

        val routes = StravaApiClient.parseRoutes(json)
        assertEquals(1, routes.size)
        val r = routes[0]
        assertEquals(22222222222L, r.id)          // exceeds Int range
        assertEquals("Strava route 22222222222", r.name)  // fallback name
        assertEquals(false, r.starred)            // defaults
        assertEquals(0.0, r.elevationGainMeters, 0.001)
    }

    @Test
    fun parseRoutes_emptyArray() {
        assertTrue(StravaApiClient.parseRoutes("[]").isEmpty())
    }

    @Test
    fun parseRoutes_fallsBackToIdStr() {
        val json = """[ { "id_str": "999", "name": "R" } ]"""
        val routes = StravaApiClient.parseRoutes(json)
        assertEquals(999L, routes[0].id)
    }

    @Test
    fun parseRoutes_skipsEntriesWithoutId() {
        val json = """[ { "name": "no id here" }, { "id": 5, "name": "ok" } ]"""
        val routes = StravaApiClient.parseRoutes(json)
        assertEquals(1, routes.size)
        assertEquals(5L, routes[0].id)
    }

    @Test
    fun parseRoutes_returnsEmptyOnInvalidJson() {
        assertTrue(StravaApiClient.parseRoutes("not json").isEmpty())
        assertTrue(StravaApiClient.parseRoutes("{}").isEmpty())
    }

    // --- network paths (MockEngine) ---

    @Test
    fun listRoutes_throwsAuthErrorOn401() = runTest {
        val client = HttpClient(MockEngine { respond("Unauthorized", HttpStatusCode.Unauthorized) })
        val ex = assertFailsWith<StravaApiException> { StravaApiClient(client).listRoutes("t", 1L) }
        assertTrue(ex.isAuthError)
    }

    @Test
    fun listRoutes_followsPagination() = runTest {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val page1 = "[" + (1..2).joinToString(",") { """{"id":$it,"name":"R$it"}""" } + "]"
        val page2 = """[{"id":3,"name":"R3"}]"""
        val client = HttpClient(MockEngine { req ->
            val page = req.url.parameters["page"]
            respond(if (page == "1") page1 else page2, HttpStatusCode.OK, jsonHeaders)
        })
        val routes = StravaApiClient(client).listRoutes("t", 1L, perPage = 2)
        assertEquals(listOf(1L, 2L, 3L), routes.map { it.id })
    }

    @Test
    fun exportRouteGpx_returnsBodyAndThrowsOnError() = runTest {
        val ok = HttpClient(MockEngine { respond("<gpx/>", HttpStatusCode.OK) })
        assertEquals("<gpx/>", StravaApiClient(ok).exportRouteGpx("t", 5L))

        val notFound = HttpClient(MockEngine { respond("nope", HttpStatusCode.NotFound) })
        assertFailsWith<StravaApiException> { StravaApiClient(notFound).exportRouteGpx("t", 5L) }
    }
}
