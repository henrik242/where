package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.synth.where.data.geo.LatLng
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(CoverageLookup.Found(CoverageAtPoint(1, 2, 3)), CoverageTilesClient.getCoverage(point, "v-read"))
        val tile = TileUtils.latLngToTileCoord(point.latitude, point.longitude, 12)
        assertEquals("https://erdetdekning.no/tiles/v-read/levels/12/${tile.x}/${tile.y}.png", requests.single())
    }

    @Test
    fun readsTheRightPixel() = runTest {
        // Telenor's level is the pixel's x mod 4, Telia's its y mod 4.
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 256) for (x in 0 until 256) image.setRGB(x, y, (0xFF shl 24) or ((x and 3) shl 16) or ((y and 3) shl 8))
        serve(HttpStatusCode.OK, ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray())
        val tile = TileUtils.latLngToTileCoord(point.latitude, point.longitude, 12)
        val expected = CoverageAtPoint(telenor = tile.pixelX and 3, telia = tile.pixelY and 3, ice = 0)
        assertEquals(CoverageLookup.Found(expected), CoverageTilesClient.getCoverage(point, "v-pixel"))
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
        assertEquals(CoverageLookup.Found(null), CoverageTilesClient.getCoverage(point, "v-404"))
        assertEquals(CoverageLookup.Found(null), CoverageTilesClient.getCoverage(point, "v-404"))
        assertEquals(1, requests.size)
    }

    @Test
    fun serverErrorIsAFailureAndIsRetried() = runTest {
        serve(HttpStatusCode.InternalServerError)
        assertEquals(CoverageLookup.Failed, CoverageTilesClient.getCoverage(point, "v-500"))
        assertEquals(CoverageLookup.Failed, CoverageTilesClient.getCoverage(point, "v-500"))
        assertEquals(2, requests.size)
    }

    @Test
    fun nonPngIsAFailureAndIsRetried() = runTest {
        serve(HttpStatusCode.OK, "<html>Log in to the Wi-Fi</html>".encodeToByteArray())
        assertEquals(CoverageLookup.Failed, CoverageTilesClient.getCoverage(point, "v-html"))
        assertEquals(CoverageLookup.Failed, CoverageTilesClient.getCoverage(point, "v-html"))
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
