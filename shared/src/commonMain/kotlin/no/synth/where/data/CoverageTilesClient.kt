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
import no.synth.where.BuildInfo
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger

/** Coverage from erdetdekning.no: vector tiles for the overlay, versioned levels tiles for lookups. */
object CoverageTilesClient {
    var client: HttpClient = createDefaultHttpClient()

    const val BASE_URL = "https://erdetdekning.no/tiles"
    const val SOURCE_ID = "coverage"

    /** The finest zoom served, about 20 m per pixel; MapLibre overzooms past it. */
    const val NATIVE_ZOOM = 12

    /** Used until the first successful lookup. Bump now and then; old versions are kept a while. */
    const val FALLBACK_VERSION = "2026-10-13"

    private const val TILE_TIMEOUT_MS = 5_000L

    private val VERSION_REGEX = Regex("""^[0-9A-Za-z._-]+$""")

    /** The last tile fetched (null for a 404), so panning within it needs no fetch. */
    @Volatile
    private var lastTile: Pair<String, RgbImage?>? = null

    /** TileJSON for [operator]'s vector layer; each feature has a "call" level, 0 to 3. */
    fun tileJsonUrl(operator: CoverageOperator) = "https://erdetdekning.no/maptiles/v1/${operator.tileLayer}.json?key=${BuildInfo.ERDETDEKNING_MAP_KEY}"

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

    /** Coverage at [latLng], or [CoverageLookup.Failed] when the tile can't be fetched. */
    suspend fun getCoverage(latLng: LatLng, version: String): CoverageLookup = try {
        val coord = TileUtils.latLngToTileCoord(latLng.latitude, latLng.longitude, NATIVE_ZOOM)
        val rgb = levelsTile("$BASE_URL/$version/levels/${coord.z}/${coord.x}/${coord.y}.png")?.rgbAt(coord.pixelX, coord.pixelY)
        CoverageLookup.Found(rgb?.let { CoverageAtPoint.fromRgb(it) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e, "Coverage lookup failed")
        CoverageLookup.Failed
    }

    private suspend fun levelsTile(url: String): RgbImage? {
        lastTile?.let { (cachedUrl, image) -> if (cachedUrl == url) return image }
        val response = client.get(url) { timeout { requestTimeoutMillis = TILE_TIMEOUT_MS } }
        // Tiles with no Norwegian land (sea, abroad) don't exist, so a 404 is an answer worth keeping.
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) {
            error("Coverage tile HTTP ${response.status.value}")
        }
        val image = if (response.status.isSuccess()) {
            val bytes = response.bodyAsBytes()
            // A 200 that isn't a PNG (a captive portal, say) is a failure, not "no data here".
            withContext(Dispatchers.Default) { PngDecoder.decode(bytes) } ?: error("Coverage tile is not a PNG")
        } else {
            null
        }
        lastTile = url to image
        return image
    }
}
