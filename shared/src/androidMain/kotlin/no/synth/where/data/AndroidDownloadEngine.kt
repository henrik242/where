package no.synth.where.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * [DownloadEngine] backed by MapLibre's offline manager plus the DEM tile downloader. Bridges
 * [MapDownloadManager]'s callback API into the suspend contract the queue expects, running the
 * map-tile pyramid and the elevation tiles in parallel.
 */
class AndroidDownloadEngine(context: Context) : DownloadEngine {
    private val manager = MapDownloadManager(context)
    private var activeRegionId: String? = null

    override suspend fun download(
        item: QueuedDownload,
        onProgress: (mapPercent: Int, demPercent: Int) -> Unit,
    ): Boolean = coroutineScope {
        activeRegionId = item.id
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

        val mapResult = CompletableDeferred<Boolean>()
        manager.downloadRegion(
            region = item.region,
            layerName = item.layerId,
            minZoom = item.minZoom,
            maxZoom = item.maxZoom,
            onProgress = { percent ->
                mapPercent = percent
                onProgress(mapPercent, demPercent)
            },
            onComplete = { success -> mapResult.complete(success) },
        )

        try {
            val mapOk = mapResult.await()
            demJob?.await()
            mapOk
        } finally {
            activeRegionId = null
        }
    }

    override fun cancelActive() {
        activeRegionId?.let { manager.stopDownload(it) }
    }
}
