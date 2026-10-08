package no.synth.where.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import no.synth.where.util.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflineManagerState
import org.maplibre.compose.offline.OfflinePack

/**
 * Coverage/stats/deletion over maplibre-compose's [org.maplibre.compose.offline.OfflineManager].
 * Packs are keyed by [QueuedDownload.id] ("regionName-layerId"), stored as the pack metadata by
 * [MaplibreComposeDownloadEngine]. Every query awaits manager readiness so a startup call can't
 * mistake a still-loading database for an empty one.
 */
object OfflineCoverage {
    private val manager get() = DefaultMapRuntime.instance.offlineManager

    private const val PROGRESS_TIMEOUT_MS = 1500L

    /** The dir maplibre-compose keeps its cache db in; set at startup so [cacheSize] can measure it. */
    var cacheDir: PlatformFile? = null

    /**
     * Total on-disk size of the shared tile cache (ambient browsing + offline packs live in the same
     * maplibre-compose db), so the "clear cache" UI reflects real usage rather than only packs.
     */
    fun cacheSize(): Long {
        val dir = cacheDir ?: return 0L
        return runCatching {
            dir.listFiles().filter { it.path.substringAfterLast('/').contains("maplibre", ignoreCase = true) }
                .sumOf { it.length() }
        }.getOrDefault(0L)
    }

    private suspend fun packs(): Set<OfflinePack> =
        when (val s = manager.state.first { it !is OfflineManagerState.Loading }) {
            is OfflineManagerState.Ready -> s.packs
            else -> emptySet()
        }

    private fun OfflinePack.idString(): String? {
        val raw = metadata.value?.decodeToString() ?: return null
        // Legacy native packs stored JSON ({"name":"<region>-<layer>", ...}); new packs store the
        // raw id. Accept both so retained downloads still match.
        if (!raw.startsWith("{")) return raw
        return runCatching { Json.parseToJsonElement(raw).jsonObject["name"]?.jsonPrimitive?.content }
            .getOrNull() ?: raw
    }

    private suspend fun packFor(regionName: String, layerId: String): OfflinePack? {
        val id = "$regionName-$layerId"
        return packs().firstOrNull { it.idString() == id }
    }

    /** Wait briefly for the pack to report real progress; a restored pack starts [Unknown]. */
    private suspend fun progressOf(pack: OfflinePack): DownloadProgress =
        withTimeoutOrNull(PROGRESS_TIMEOUT_MS) {
            pack.downloadProgress.first { it !is DownloadProgress.Unknown }
        } ?: pack.downloadProgress.value

    private suspend fun tileInfoOf(pack: OfflinePack, estimatedTiles: Int): RegionTileInfo =
        when (val p = progressOf(pack)) {
            is DownloadProgress.Healthy -> {
                val required = p.requiredResourceCount.toInt()
                RegionTileInfo(
                    totalTiles = if (required > 0) required else maxOf(p.completedResourceCount.toInt(), estimatedTiles),
                    downloadedTiles = p.completedResourceCount.toInt(),
                    downloadedSize = p.completedTileBytes,
                    isFullyDownloaded = p.status == DownloadStatus.Complete,
                )
            }
            // Unknown/Error/TileLimitExceeded: the pack exists but completion is unconfirmed. Report
            // it present-but-incomplete so the UI still offers download/continue rather than hiding it.
            else -> RegionTileInfo(totalTiles = estimatedTiles, downloadedTiles = 0, downloadedSize = 0, isFullyDownloaded = false)
        }

    /** Region name -> tile info for every pack of [layerId]. */
    suspend fun regionInfosForLayer(layerId: String): Map<String, RegionTileInfo> {
        val suffix = "-$layerId"
        return packs().mapNotNull { pack ->
            val id = pack.idString() ?: return@mapNotNull null
            if (!id.endsWith(suffix)) return@mapNotNull null
            id.dropLast(suffix.length) to tileInfoOf(pack, estimatedTiles = 0)
        }.toMap()
    }

    suspend fun downloadedRegionsForLayer(layerId: String): Set<String> =
        regionInfosForLayer(layerId).keys

    suspend fun regionTileInfo(regionName: String, layerId: String, estimatedTiles: Int): RegionTileInfo {
        val pack = packFor(regionName, layerId) ?: return RegionTileInfo(estimatedTiles, 0, 0, false)
        return tileInfoOf(pack, estimatedTiles)
    }

    suspend fun deleteRegion(regionName: String, layerId: String): Boolean {
        val pack = packFor(regionName, layerId) ?: return false
        return runCatching { manager.delete(pack) }
            .onFailure { Logger.e(it, "Failed to delete offline region %s/%s", regionName, layerId) }
            .isSuccess
    }

    suspend fun deleteAllForLayer(layerId: String): Boolean {
        val suffix = "-$layerId"
        return runCatching {
            packs().filter { it.idString()?.endsWith(suffix) == true }.forEach { manager.delete(it) }
        }.onFailure { Logger.e(it, "Failed to delete offline packs for layer %s", layerId) }.isSuccess
    }

    /** True if any OTHER layer has a pack for the same hex region. */
    suspend fun hasOtherLayers(regionHexId: String, excludeLayer: String): Boolean {
        val prefix = "$regionHexId-"
        val excludeId = "$regionHexId-$excludeLayer"
        return packs().any { it.idString()?.let { id -> id.startsWith(prefix) && id != excludeId } == true }
    }

    suspend fun layerStats(layerId: String): LayerStats {
        val suffix = "-$layerId"
        var bytes = 0L
        var tiles = 0
        packs().forEach { pack ->
            if (pack.idString()?.endsWith(suffix) != true) return@forEach
            (progressOf(pack) as? DownloadProgress.Healthy)?.let {
                bytes += it.completedTileBytes
                tiles += it.completedTileCount.toInt()
            }
        }
        return LayerStats(bytes, tiles)
    }

    suspend fun clearAmbientCache() {
        runCatching { manager.clearAmbientCache() }
            .onFailure { Logger.e(it, "Failed to clear ambient cache") }
    }

    suspend fun setMaxAmbientCacheSize(bytes: Long) {
        manager.setMaximumAmbientCacheSize(bytes)
    }
}
