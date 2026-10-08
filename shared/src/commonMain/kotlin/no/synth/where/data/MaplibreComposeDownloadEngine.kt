package no.synth.where.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.OfflineManager
import org.maplibre.compose.offline.OfflineManagerState
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.DownloadStatus as MlnDownloadStatus
import org.maplibre.spatialk.geojson.BoundingBox
import no.synth.where.util.Logger

/**
 * [DownloadEngine] backed by maplibre-compose's [OfflineManager] for the map tiles, plus the shared
 * [OfflineTileReader] for DEM tiles (unchanged). Replaces the per-platform native offline managers
 * and the local StyleServer: the pack's style is written to a cache file and referenced by a
 * `file://` URL.
 *
 * @param cacheDir a writable directory for the transient per-layer style files.
 */
class MaplibreComposeDownloadEngine(
    private val cacheDir: PlatformFile,
    private val pixelRatio: Float = 2.0f,
) : DownloadEngine {

    private val offlineManager: OfflineManager
        get() = DefaultMapRuntime.instance.offlineManager

    private var activePack: OfflinePack? = null

    override suspend fun download(
        item: QueuedDownload,
        onProgress: (mapPercent: Int, demPercent: Int) -> Unit,
    ): Boolean = coroutineScope {
        var mapPercent = 0
        var demPercent = if (item.downloadDem) 0 else -1

        val demJob = if (item.downloadDem) {
            async {
                OfflineTileReader.downloadDemTilesForBounds(item.region.boundingBox) { percent ->
                    demPercent = percent
                    onProgress(mapPercent, demPercent)
                }
            }
        } else {
            null
        }

        var pack: OfflinePack? = null
        val mapOk =
            try {
                val created = createPack(item)
                pack = created
                activePack = created
                offlineManager.resume(created)
                awaitComplete(created) { percent ->
                    mapPercent = percent
                    onProgress(mapPercent, demPercent)
                }
            } catch (e: CancellationException) {
                pack?.let { withContext(NonCancellable) { runCatching { offlineManager.delete(it) } } }
                demJob?.cancel()
                throw e
            } catch (e: Throwable) {
                // A create/style-write/resume failure has no useful partial pack: drop it so it does
                // not linger, and fail the download instead of cancelling the whole queue drain.
                Logger.e(e, "Offline map download failed for %s", item.id)
                pack?.let { withContext(NonCancellable) { runCatching { offlineManager.delete(it) } } }
                pack = null
                false
            } finally {
                activePack = null
            }

        if (!mapOk) {
            // A download-phase failure (e.g. a transient network error) keeps its partial pack,
            // paused, so a retry resumes it rather than re-downloading; otherwise Native would keep
            // retrying in the background after we stop waiting.
            pack?.let { withContext(NonCancellable) { runCatching { offlineManager.pause(it) } } }
        }
        // A DEM failure must not bubble up and cancel the queue either.
        runCatching { demJob?.await() }
        mapOk
    }

    override fun cancelActive() {
        activePack?.let { runCatching { offlineManager.pause(it) } }
    }

    private suspend fun createPack(item: QueuedDownload): OfflinePack {
        // Reuse an existing pack with the same id (e.g. a partial download resumed after a failure or
        // a process death) instead of creating a duplicate definition for the same region.
        existingPack(item.id)?.let { return it }
        val styleJson = DownloadLayers.getDownloadStyleJson(item.layerId)
        val styleFile = cacheDir.resolve("offline-style-${item.layerId}.json")
        styleFile.writeBytes(styleJson.encodeToByteArray())
        val bounds = item.region.boundingBox
        val definition =
            OfflinePackDefinition.TilePyramid(
                styleUrl = "file://${styleFile.path}",
                bounds = BoundingBox(
                    west = bounds.west,
                    south = bounds.south,
                    east = bounds.east,
                    north = bounds.north,
                ),
                pixelRatio = pixelRatio,
                minZoom = item.minZoom,
                maxZoom = DownloadLayers.effectiveMaxZoom(item.layerId, item.maxZoom),
            )
        return offlineManager.create(definition, metadata = item.id.encodeToByteArray())
    }

    private suspend fun existingPack(id: String): OfflinePack? {
        val state = offlineManager.state.first { it !is OfflineManagerState.Loading }
        val packs = (state as? OfflineManagerState.Ready)?.packs ?: return null
        return packs.firstOrNull { it.metadata.value?.decodeToString() == id }
    }

    /** Suspends until the pack finishes, reporting 0..100 along the way. Returns success. */
    private suspend fun awaitComplete(pack: OfflinePack, onPercent: (Int) -> Unit): Boolean {
        val terminal =
            pack.downloadProgress.first { progress ->
                when (progress) {
                    is DownloadProgress.Healthy -> {
                        onPercent(percentOf(progress))
                        progress.status == MlnDownloadStatus.Complete
                    }
                    is DownloadProgress.Error -> true
                    is DownloadProgress.TileLimitExceeded -> true
                    DownloadProgress.Unknown -> false
                }
            }
        return terminal is DownloadProgress.Healthy && terminal.status == MlnDownloadStatus.Complete
    }

    private fun percentOf(p: DownloadProgress.Healthy): Int {
        val required = p.requiredResourceCount
        if (required <= 0) return 0
        return ((p.completedResourceCount * 100) / required).toInt().coerceIn(0, 100)
    }
}
