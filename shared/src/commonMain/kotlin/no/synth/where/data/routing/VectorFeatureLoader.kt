package no.synth.where.data.routing

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.MapStyle
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.TerrainClient
import no.synth.where.util.Logger
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/** Burns lakes, rivers, streams, paths, bridges and wetland from OpenFreeMap tiles into a grid. */
object VectorFeatureLoader {
    /** OpenFreeMap's max zoom; the only one carrying every stream and path. */
    private const val TILE_ZOOM = 14
    private const val CACHE_SUBDIR = "vector-tiles"

    @Volatile
    private var tileUrlTemplate: String? = null

    /** Returns the number of tiles that couldn't be loaded; 0 means the grid has full feature data. */
    suspend fun burnInto(grid: TerrainGrid): Int = withContext(Dispatchers.Default) {
        val template = resolveTemplate()
        val shift = TILE_ZOOM - grid.zoom
        val tileCells = Mercator.TILE_SIZE shr shift
        val tx0 = grid.originPx / tileCells
        val ty0 = grid.originPy / tileCells
        val tx1 = (grid.originPx + grid.width - 1) / tileCells
        val ty1 = (grid.originPy + grid.height - 1) / tileCells
        val tiles = (ty0..ty1).flatMap { ty -> (tx0..tx1).map { tx -> tx to ty } }

        val semaphore = Semaphore(6)
        val decoded = coroutineScope {
            tiles.map { (tx, ty) ->
                async {
                    semaphore.withPermit {
                        val bytes = OfflineTileReader.readCachedTile(template, TILE_ZOOM, tx, ty, CACHE_SUBDIR, "pbf")
                        bytes?.let {
                            try {
                                MvtDecoder.decode(it, FeatureRasterizer.LAYERS)
                            } catch (e: Exception) {
                                Logger.e(e, "Failed to decode vector tile %d/%d", tx, ty)
                                null
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        // Flags are a shared array, so burn sequentially.
        var missing = 0
        tiles.forEachIndexed { i, (tx, ty) ->
            val layers = decoded[i]
            if (layers == null) missing++ else FeatureRasterizer.burnTile(grid, layers, TILE_ZOOM, tx, ty)
        }
        missing
    }

    /** The TileJSON names a dated build; offline we fall back to whatever is on disk. */
    private suspend fun resolveTemplate(): String {
        tileUrlTemplate?.let { return it }
        if (OfflineTileReader.offlineOnly) return ""
        return try {
            val json = Json.parseToJsonElement(TerrainClient.client.get(MapStyle.OSM_PATHS_TILEJSON_URL).bodyAsText())
            val url = json.jsonObject["tiles"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
            if (url.isNotEmpty()) tileUrlTemplate = url
            url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, "Failed to resolve vector TileJSON")
            ""
        }
    }
}
