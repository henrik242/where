package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class OnlineTrackingClientTest {

    @Test
    fun startTrack_buildsCorrectJson() = runBlocking {
        var capturedBody: String? = null
        var capturedMethod: HttpMethod? = null
        var capturedClientIdHeader: String? = null

        val client = OnlineTrackingClient(
            serverUrl = "https://example.com",
            clientId = "test-client",
            trackingHint = "test-secret",
            client = HttpClient(MockEngine { request ->
                if (request.url.encodedPath.contains("viewers")) {
                    respond("""{"viewers":0}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    capturedBody = String(request.body.toByteArray())
                    capturedMethod = request.method
                    capturedClientIdHeader = request.headers["X-Client-Id"]
                    respond("""{"id":"track-123"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            })
        )

        client.startTrack("Morning Hike")
        delay(500)

        assertNotNull(capturedBody)
        val json = Json.parseToJsonElement(requireNotNull(capturedBody)).jsonObject
        assertEquals("test-client", json["userId"]?.jsonPrimitive?.content)
        assertEquals("Morning Hike", json["name"]?.jsonPrimitive?.content)
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("test-client", capturedClientIdHeader)
    }

    @Test
    fun startTrack_sendsSignatureHeader() = runBlocking {
        var capturedSignature: String? = null

        val client = OnlineTrackingClient(
            serverUrl = "https://example.com",
            clientId = "test-client",
            trackingHint = "test-secret",
            client = HttpClient(MockEngine { request ->
                if (request.url.encodedPath.contains("viewers")) {
                    respond("""{"viewers":0}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    capturedSignature = request.headers["X-Signature"]
                    respond("""{"id":"track-456"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            })
        )

        client.startTrack("Test")
        delay(500)

        assertNotNull("Signature header should be present", capturedSignature)
        assertTrue("Signature should be non-empty", requireNotNull(capturedSignature).isNotBlank())
    }

    @Test
    fun stopTrack_sendsPutRequest() = runBlocking {
        var startCalled = false
        var capturedStopMethod: HttpMethod? = null
        var capturedStopUrl: String? = null

        val client = OnlineTrackingClient(
            serverUrl = "https://example.com",
            clientId = "test-client",
            trackingHint = "test-secret",
            client = HttpClient(MockEngine { request ->
                if (request.url.encodedPath.contains("viewers")) {
                    respond("""{"viewers":0}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                } else if (!startCalled) {
                    startCalled = true
                    respond("""{"id":"track-789"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    capturedStopMethod = request.method
                    capturedStopUrl = request.url.toString()
                    respond("{}", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            })
        )

        client.startTrack("Test")
        delay(500)
        client.stopTrack()
        delay(500)

        assertEquals(HttpMethod.Put, capturedStopMethod)
        assertTrue(requireNotNull(capturedStopUrl).contains("/api/tracks/track-789/stop"))
    }
}
