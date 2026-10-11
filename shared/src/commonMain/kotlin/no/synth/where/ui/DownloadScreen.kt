package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import no.synth.where.data.DownloadStatus
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.UserPreferences
import no.synth.where.data.routing.OfflineTerrain
import no.synth.where.data.summary

/** The offline maps screen on every platform; [freeStorageBytes] returns -1 when unknown. */
@Composable
fun DownloadScreen(
    downloadManager: MapDownloadManager,
    userPreferences: UserPreferences,
    freeStorageBytes: () -> Long,
    onBackClick: () -> Unit,
    onLayerClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val queue by downloadManager.queue.collectAsState()
    val downloadElevationData by userPreferences.downloadElevationData.collectAsState()
    val downloadMaxZoom by userPreferences.downloadMaxZoom.collectAsState()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var cacheSize by remember { mutableLongStateOf(0L) }
    val freeStorage = remember(refreshTrigger) { freeStorageBytes() }
    val layers = rememberLayerInfos()

    // Refresh cache size + layer stats whenever the queue drains an item.
    LaunchedEffect(queue.count { it.status == DownloadStatus.COMPLETED }) { refreshTrigger++ }
    LaunchedEffect(refreshTrigger) { cacheSize = downloadManager.getCacheSize() }

    fun refreshAfter(block: suspend () -> Unit) {
        scope.launch {
            block()
            refreshTrigger++
        }
    }

    DownloadScreenContent(
        layers = layers,
        cacheSize = cacheSize,
        freeStorageBytes = freeStorage,
        queueSummary = if (queue.isEmpty()) null else queue.summary(),
        onDownloadsClick = onDownloadsClick,
        onBackClick = onBackClick,
        onLayerClick = onLayerClick,
        onDeleteLayer = { layerId -> refreshAfter { downloadManager.deleteLayer(layerId) } },
        onClearAutoCache = { refreshAfter { downloadManager.clearAutoCache() } },
        downloadElevationData = downloadElevationData,
        demCacheSize = remember(refreshTrigger) { OfflineTerrain.cacheSize() },
        onDownloadElevationDataChange = { enabled ->
            userPreferences.updateDownloadElevationData(enabled)
            if (!enabled) refreshAfter { OfflineTerrain.clear() }
        },
        downloadMaxZoom = downloadMaxZoom,
        onDownloadMaxZoomChange = { userPreferences.updateDownloadMaxZoom(it) },
        getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
        refreshTrigger = refreshTrigger,
    )
}

@Composable
fun DownloadQueueScreen(downloadManager: MapDownloadManager, onBackClick: () -> Unit) {
    val queue by downloadManager.queue.collectAsState()
    DownloadQueueScreenContent(
        queue = queue,
        onCancelDownload = { id -> downloadManager.cancel(id) },
        onClearFinished = { downloadManager.clearFinished() },
        onBackClick = onBackClick,
    )
}
