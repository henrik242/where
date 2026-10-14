package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger

/** Call coverage tiles from erdetdekning.no, which live under a dataset version. */
object CoverageTilesClient {
    var client: HttpClient = createDefaultHttpClient()

    const val BASE_URL = "https://erdetdekning.no/tiles"
    const val SOURCE_ID = "coverage"
    const val MIN_ZOOM = 5

    /** The finest zoom served, about 20 m per pixel; MapLibre overzooms past it. */
    const val NATIVE_ZOOM = 12

    /** Used until the first successful lookup. Bump now and then; old versions are kept a while. */
    const val FALLBACK_VERSION = "2026-10-06"

    private const val TILE_TIMEOUT_MS = 5_000L

    private val VERSION_REGEX = Regex("""^[0-9A-Za-z._-]+$""")

    /** The last raw tile fetched (URL to bytes, null bytes for a 404), so panning within it needs no fetch. */
    @Volatile
    private var lastTile: Pair<String, ByteArray?>? = null

    /** Coloured tiles for the map overlay. */
    fun tileUrl(version: String) = "$BASE_URL/$version/coverage/calls/{z}/{x}/{y}.png"

    suspend fun fetchVersion(): String? = try {
        val response = client.get("$BASE_URL/meta.json")
        if (response.status.isSuccess()) parseVersion(response.bodyAsText()) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e, "Coverage tiles version lookup failed")
        null
    }

    /** The version from meta.json, or null if missing or not safe to put in a URL. */
    fun parseVersion(json: String): String? = try {
        Json.parseToJsonElement(json).jsonObject["version"]?.jsonPrimitive?.content
            ?.takeIf { VERSION_REGEX.matches(it) }
    } catch (_: Exception) {
        null
    }

    /** Coverage at [latLng] (null outside Norway or at sea), or a failure when the tile can't be fetched. */
    suspend fun getCoverage(latLng: LatLng, version: String): Result<CoverageAtPoint?> = try {
        val coord = TileUtils.latLngToTileCoord(latLng.latitude, latLng.longitude, NATIVE_ZOOM)
        val png = levelsTile("$BASE_URL/$version/levels/${coord.z}/${coord.x}/${coord.y}.png")
        val rgb = png?.let { withContext(Dispatchers.Default) { PngDecoder.decode(it)?.rgbAt(coord.pixelX, coord.pixelY) } }
        Result.success(rgb?.let { CoverageAtPoint.fromRgb(it) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e, "Coverage lookup failed")
        Result.failure(e)
    }

    private suspend fun levelsTile(url: String): ByteArray? {
        lastTile?.let { (cachedUrl, bytes) -> if (cachedUrl == url) return bytes }
        val response = client.get(url) { timeout { requestTimeoutMillis = TILE_TIMEOUT_MS } }
        // Tiles with no Norwegian land (sea, abroad) don't exist, so a 404 is an answer worth keeping.
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) {
            error("Coverage tile HTTP ${response.status.value}")
        }
        val bytes = if (response.status.isSuccess()) response.bodyAsBytes() else null
        lastTile = url to bytes
        return bytes
    }
}
