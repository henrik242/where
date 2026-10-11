package no.synth.where.data.routing

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.MapStyle
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.TerrainClient
import no.synth.where.data.TileUtils
import no.synth.where.util.Logger
import no.synth.where.util.mapConcurrently
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/** Burns lakes, rivers, streams, paths, bridges and wetland from OpenFreeMap tiles into a grid. */
object VectorFeatureLoader {
    /** OpenFreeMap's max zoom; the only one carrying every stream and path. */
    const val TILE_ZOOM = 14
    const val CACHE_SUBDIR = "vector-tiles"
    const val CACHE_EXT = "pbf"

    @Volatile
    private var tileUrlTemplate: String? = null

    /** Returns the number of tiles that couldn't be loaded; 0 means the grid has full feature data. */
    suspend fun burnInto(grid: TerrainGrid): Int = withContext(Dispatchers.Default) {
        val template = tileTemplate().orEmpty()
        val shift = TILE_ZOOM - grid.zoom
        val tileCells = TileUtils.TILE_SIZE shr shift
        val tx0 = grid.originPx / tileCells
        val ty0 = grid.originPy / tileCells
        val tx1 = (grid.originPx + grid.width - 1) / tileCells
        val ty1 = (grid.originPy + grid.height - 1) / tileCells
        val tiles = (ty0..ty1).flatMap { ty -> (tx0..tx1).map { tx -> tx to ty } }

        val decoded = tiles.mapConcurrently { (tx, ty) ->
            readTile(template, tx, ty)?.let {
                try {
                    MvtDecoder.decode(it, FeatureRasterizer.LAYERS)
                } catch (e: Exception) {
                    Logger.e(e, "Failed to decode vector tile %d/%d", tx, ty)
                    null
                }
            }
        }
        // Flags are a shared array, so burn sequentially.
        var missing = 0
        tiles.forEachIndexed { i, (tx, ty) ->
            val layers = decoded[i]
            if (layers == null) missing++ else FeatureRasterizer.burnTile(grid, layers, TILE_ZOOM, tx, ty)
        }
        missing
    }

    /** Cached tile, else fetched with only the router's layers kept (~75% smaller in towns). */
    private suspend fun readTile(template: String, x: Int, y: Int): ByteArray? =
        OfflineTileReader.readCachedTile(template, TILE_ZOOM, x, y, CACHE_SUBDIR, CACHE_EXT, ::stripTile)

    suspend fun ensureCached(template: String, x: Int, y: Int) =
        OfflineTileReader.ensureCached(template, TILE_ZOOM, x, y, CACHE_SUBDIR, CACHE_EXT, ::stripTile)

    private fun stripTile(data: ByteArray) = MvtDecoder.keepLayers(data, FeatureRasterizer.LAYERS)

    /**
     * Tile URL template from the TileJSON, which names a dated build. Null when it can't be
     * resolved (offline): tiles already on disk are still read, they're cached by z/x/y only.
     */
    suspend fun tileTemplate(): String? {
        tileUrlTemplate?.let { return it }
        if (OfflineTileReader.offlineOnly) return null
        return try {
            val json = Json.parseToJsonElement(TerrainClient.client.get(MapStyle.OSM_PATHS_TILEJSON_URL).bodyAsText())
            json.jsonObject["tiles"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                ?.takeIf { it.isNotEmpty() }
                ?.also { tileUrlTemplate = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, "Failed to resolve vector TileJSON")
            null
        }
    }
}
