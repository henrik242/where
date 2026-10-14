package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.synth.where.data.geo.LatLng
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Each test uses its own dataset version, so the client's last-tile cache never carries over. */
class CoverageTilesClientNetworkTest {
    private val originalClient = CoverageTilesClient.client
    private val requests = mutableListOf<String>()
    private val point = LatLng(61.0, 9.0)

    private fun serve(status: HttpStatusCode, body: ByteArray = ByteArray(0)) {
        CoverageTilesClient.client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond(body, status)
        }) { install(HttpTimeout) }
    }

    @AfterTest
    fun restoreClient() {
        CoverageTilesClient.client = originalClient
    }

    @Test
    fun readsThePixelUnderThePointFromTheVersionedTile() = runTest {
        serve(HttpStatusCode.OK, uniformTile(0xFF010203.toInt()))
        assertEquals(CoverageAtPoint(1, 2, 3), CoverageTilesClient.getCoverage(point, "v-read").getOrThrow())
        val tile = TileUtils.latLngToTileCoord(point.latitude, point.longitude, 12)
        assertEquals("https://erdetdekning.no/tiles/v-read/levels/12/${tile.x}/${tile.y}.png", requests.single())
    }

    @Test
    fun reusesTheLastTile() = runTest {
        serve(HttpStatusCode.OK, uniformTile(0xFF000001.toInt()))
        CoverageTilesClient.getCoverage(point, "v-reuse")
        CoverageTilesClient.getCoverage(point, "v-reuse")
        assertEquals(1, requests.size)
    }

    @Test
    fun missingTileIsNoDataAndIsKept() = runTest {
        serve(HttpStatusCode.NotFound)
        assertNull(CoverageTilesClient.getCoverage(point, "v-404").getOrThrow())
        assertNull(CoverageTilesClient.getCoverage(point, "v-404").getOrThrow())
        assertEquals(1, requests.size)
    }

    @Test
    fun serverErrorIsAFailureAndIsRetried() = runTest {
        serve(HttpStatusCode.InternalServerError)
        assertTrue(CoverageTilesClient.getCoverage(point, "v-500").isFailure)
        assertTrue(CoverageTilesClient.getCoverage(point, "v-500").isFailure)
        assertEquals(2, requests.size)
    }

    @Test
    fun fetchVersionReadsMetaJson() = runTest {
        serve(HttpStatusCode.OK, """{"version":"2026-11-01"}""".encodeToByteArray())
        assertEquals("2026-11-01", CoverageTilesClient.fetchVersion())
        assertEquals("https://erdetdekning.no/tiles/meta.json", requests.single())
    }

    @Test
    fun fetchVersionFailureIsNull() = runTest {
        serve(HttpStatusCode.ServiceUnavailable)
        assertNull(CoverageTilesClient.fetchVersion())
    }
}
